# ADR 0003 — Every event goes through an outbox

**Status:** accepted

## Context

The transfer flow has a hard ordering requirement: the ledger must be committed
**before** anyone is told the money moved. A notification that arrives before the
commit is a notification about a transfer that may not exist.

The obvious implementation:

```java
@Transactional
public void settle(Transaction tx) {
    debit(tx.senderAccountId(), tx.amount());
    credit(tx.receiverAccountId(), tx.amount());
    tx.setStatus(COMPLETED);
    txRepository.save(tx);
    kafkaTemplate.send("transaction.completed", tx.getId(), event);   // ← wrong
}
```

This publishes *before* the commit. If the transaction then rolls back, the
message is already on the broker and the account-service will apply a balance
delta for money that never moved.

The mirror-image mistake is to publish *after* the commit by hand:

```java
@Transactional
public void settle(Transaction tx) { ...; txRepository.save(tx); }

public void settleAndPublish(Transaction tx) {
    settlementService.settle(tx);
    kafkaTemplate.send("transaction.completed", event);   // ← also wrong
}
```

If the JVM dies between the two lines, the balance is committed and the event is
gone forever. The account-service projection silently drifts, and the customer is
told their transfer succeeded while the receiving account still shows the old
balance. Nothing detects it.

## Decision

Never publish from inside a business transaction. Write the event to a table in
the same transaction, and let a scheduled job publish it.

```sql
CREATE TABLE outbox_event (
    id           uuid PRIMARY KEY,
    topic        varchar(60)  NOT NULL,
    event_key    varchar(80)  NOT NULL,
    dedupe_key   varchar(160) NOT NULL UNIQUE,
    payload      jsonb        NOT NULL,
    created_at   timestamptz  NOT NULL,
    published_at timestamptz,
    attempts     int          NOT NULL DEFAULT 0
);
```

```java
@Transactional
public void settle(Transaction tx) {
    lockBothLedgerRowsInAscendingOrder(tx);
    revalidateStatusAndBalance(tx);
    debitAndCredit(tx);
    writeLedgerEventRows(tx);
    tx.setStatus(COMPLETED);
    outboxService.enqueue(Topics.TRANSACTION_COMPLETED, eventFor(tx));  // same tx
    outboxService.enqueue(Topics.AUDIT_RECORDED, auditFor(tx));          // same tx
}   // ← commit

@Scheduled(fixedDelayString = "${finova.transactions.outbox-interval-ms:500}")
public void publishPending() {
    outboxRepository.findUnpublished(100).forEach(this::publishAndMark);
}
```

`OutboxService.enqueue` is `@Transactional(propagation = MANDATORY)`, so it is
physically impossible to call it outside a business transaction — the mistake
becomes a compile-time-ish error rather than a silent bug.

## The failure modes this covers

| Failure | Without outbox | With outbox |
|---|---|---|
| Broker down at publish time | Event lost, projection drifts forever | Row stays pending, retried every cycle |
| JVM dies between commit and publish | Event lost forever | Row committed with the state change, published by the next process |
| Rollback after publish | Consumers act on money that never moved | Event was never published |
| Duplicate publish | Double-applied deltas | `dedupe_key` unique constraint + consumer idempotency |

## Consequences

- **Delivery is at-least-once, not exactly-once.** Every consumer must be
  idempotent, and they are: the account projection uses a
  `balance_projection` marker table, the fraud service has a
  `decisionPublished` flag and a dedupe key on its outbox, the notification
  service has a partial unique index on `source_event_id`, and the transaction
  service has `transaction_event_marker UNIQUE (topic, event_id)`.
- **Up to ~500 ms of latency** between commit and event. Irrelevant here — the
  flow already has a fraud hop in the middle.
- **An operational table** that needs monitoring: unpublished rows older than a
  threshold are a real alert, and after `outbox-max-attempts` a poison row is
  logged and dropped rather than blocking the queue.
- **Extra writes** on the hot path. Worth it: the alternative is losing money
  events silently.