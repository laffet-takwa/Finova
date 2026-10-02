# Finova Event Flow Contract

Every service in Finova is a real participant in this flow. If a change breaks one
of the steps below, the platform is broken — treat this file as the specification.

```
Vue SPA
  │  POST /api/transactions            (Idempotency-Key: <uuid>)
  ▼
API Gateway  ── validates JWT, stamps X-Correlation-Id
  ▼
Transaction Service
  │  1. resolve sender account          (Feign → account-service)
  │  2. resolve receiver account number (Feign → account-service)
  │  3. validate ownership, status, amount, currency, balance  (optimistic read)
  │  4. INSERT transaction status=PENDING, reference TX-yyyyMMdd-NNNNN, unique idempotency_key
  │  5. COMMIT, then publish ─────────────► topic: transaction.created
  ▼
Fraud Service   (consumes transaction.created)
  │  rule engine → riskScore 0..100, riskLevel, reasons[]
  │  persists FraudAlert in MongoDB
  │
  ├── riskLevel HIGH ─────────────────────► topic: transaction.flagged
  └── riskLevel LOW / MEDIUM ──────────────► topic: transaction.approved
  ▼
Transaction Service   (consumes transaction.approved | transaction.flagged)
  │  SETTLEMENT (single @Transactional unit, ledger rows locked with PESSIMISTIC_WRITE
  │              in ascending ledger id order to avoid deadlocks):
  │     • re-verify sender + receiver status == ACTIVE
  │     • re-verify sender balance >= amount
  │     • ledger[receiver].balance += amount ; ledger[sender].balance -= amount
  │     • transaction.status = COMPLETED ; completedAt = now
  │  ON FAILURE: transaction.status = FAILED, no money movement, reason recorded
  │  publishes ──► topic: transaction.completed  (the settlement FACT)
  ▼
Account Service   (consumes transaction.completed | transaction.failed)
  │  updates its READ PROJECTION of the balance and publishes account.blocked
  │  when an administrator or a fraud review blocks the account
  ▼
Notification Service   (consumes transaction.completed | transaction.failed |
  │                     transaction.flagged | account.blocked)
  │  INSERT notification rows, publish ──► topic: notification.created
  ▼
User Service   (consumes audit.recorded) → append-only audit trail
```

## Why approval and completion are separate topics

`transaction.approved` is the fraud engine's **decision to proceed**.
`transaction.completed` is the **settlement fact** — the money has moved.

Collapsing them onto one topic would be ambiguous: a consumer could not tell
whether to settle, to notify, or both, and at-least-once redelivery would
risk notifying a customer about money that never moved. Splitting them means:

- the **transaction service** consumes only `transaction.approved` (the trigger to settle),
  and never consumes its own `transaction.completed` output;
- the **notification service** consumes only terminal facts
  (`transaction.completed`, `transaction.failed`, `transaction.flagged`) and can
  never react to an approval as if the payment had succeeded.

An administrator marking a flagged alert "safe" republishes
`transaction.approved`, which is exactly the trigger that releases held funds.

## Topic inventory

See "Topic inventory" above.

## Balance ownership — the single most important rule

| Concern                                    | Owner               |
|--------------------------------------------|---------------------|
| Product: holder, type, currency, status      | **account-service** |
| Authoritative balance and money movement     | **transaction-service** (`ledger_account` + `ledger_event`) |
| Read projection of the balance for the UI    | **account-service** (`account.balance`) |

`account-service` never mutates a balance because of a user request. It only:
- assigns the opening balance at account creation and publishes `account.opened`;
- applies the balance delta published on `transaction.completed`.

Therefore `GET /api/accounts/{id}/balance` is eventually consistent (sub-second in
practice). The transfer response returns the **post-settlement balances of both legs**,
so the UI can update its account store immediately and correctly.

## Idempotency

1. `Idempotency-Key` header is mandatory on `POST /api/transactions`.
2. A unique index on `transaction.idempotency_key` enforces "one transfer per key".
3. A replay with the **same** key returns `200` with the original transaction
   plus an `Idempotent-Replay: true` response header.
4. A replay with the **same key but a different payload** returns `409
   IDEMPOTENCY_KEY_REUSED`. Compare a SHA-256 of the canonical request payload.
5. Kafka consumers dedupe on `eventId` / business id, so at-least-once delivery
   never double-settles: settlement additionally guards on
   `transaction.status == PENDING` inside the locked unit of work, and writes a
   `transaction_event_marker` row with `UNIQUE (topic, event_id)`.

## Publish after commit — the outbox pattern

No service publishes to Kafka from inside a business transaction. The state
change and the event are written **in the same database transaction** to an
`outbox_event` table, and a scheduled publisher drains it afterwards:

```
@Transactional
settle(...) {
    ... update balances, insert ledger_event rows, mark COMPLETED ...
    outboxService.enqueue("transaction.completed", event);   // same tx
    outboxService.enqueue("audit.recorded", event);          // same tx
}                                                             // commit

@Scheduled(fixedDelay = 500)
publishPendingOutbox() { ... send, then mark published_at ... }
```

This is what makes a broker outage survivable: the state is committed, the event
is queued, and delivery retries. Every service that produces events (transaction,
account, fraud, notification, user) uses this pattern.

## Settlement safety

- `ledger_account` has a `version` column (`@Version`) **and** is locked with
  `PESSIMISTIC_WRITE` — pessimistic is the effective guarantee.
- Rows are always locked in ascending `ledger_account.id` order regardless of
  transfer direction. Two transfers in opposite directions therefore acquire the
  same pair in the same global order, so no wait-for cycle can form and deadlock
  is impossible rather than merely unlikely.
- The transfer rejects with `422 INSUFFICIENT_BALANCE` at both the pre-check and
  the settlement step; the settlement check is the authoritative one.
- A transfer is never left non-terminal: the settlement failure path writes
  `FAILED` + `failureReason` + `transaction.failed` + `audit.recorded` in the
  same transaction.
- A `PENDING` transaction that never receives a decision is re-driven by a
  scheduled reconciler in the transaction service, which republishes
  `transaction.created`. The fraud service dedupes on `transactionId`, so the
  flow always converges.
- Human-readable references (`TX-<yyyyMMdd>-<5 digits>`) come from an atomic
  per-day sequence (`INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING`), so
  they are unique, sortable and safe under concurrency.

## Known gaps (deliberate, documented rather than faked)

1. **Confirmed fraud does not auto-reject the transfer.** `fraud-service` sets the
   alert to `CONFIRMED` and keeps the funds held; it cannot emit a rejection,
   because no such topic exists and the transaction service owns the failure
   path. Completing this needs a `transaction.rejected` topic carrying the
   `TransactionEvent` originally published as `transaction.created`, plus a
   terminal-status guard on settlement.
2. **Customer-facing balances are eventually consistent.** The authoritative
   balance lives in the transaction service's ledger; the account service holds
   a projection updated from `transaction.completed`.
3. **No fee schedule.** `fee` and `totalAmount` exist on the model and are always
   `0.000`; the review UI states this plainly rather than hiding the row.
