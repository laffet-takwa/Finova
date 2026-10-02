# ADR 0004 — Global lock ordering makes deadlock impossible

**Status:** accepted

## Context

Settlement locks two ledger rows and writes to both. Two transfers running
concurrently in opposite directions do the same thing with the same pair:

```
Transfer A→B:  locks row A, then wants row B
Transfer B→A:  locks row B, then wants row A
```

Both hold one row and wait for the other. PostgreSQL detects the cycle and kills
one transaction with `DeadlockLoserDataAccessException`.

## The tempting fix, and why it is not enough

Retry on deadlock. The standard advice, and it does work:

```java
catch (DeadlockLoserDataAccessException ex) { retry(); }
```

But "retry until it works" turns a correctness problem into an availability and
latency problem:

- Under sustained contention the retry rate climbs and p99 latency explodes
  invisibly, because no single request fails — they all just get slower
- A retry storm amplifies exactly the load that caused the deadlock
- Every retry re-runs the whole settlement, including the writes
- Most importantly: it treats a *design* problem as a *runtime* condition

## Decision

Acquire multi-row locks in a **global ascending order**, always.

```java
@Transactional
public void settle(Transaction tx) {
    if (markers.existsByTopicAndEventId(topic, eventId)) return;

    // Read the two primary keys WITHOUT locking, so we know the order.
    // (findByAccountIdNoLock)
    List<String> orderedIds = Stream.of(tx.getSenderAccountId(), tx.getReceiverAccountId())
            .sorted()                       // ascending, deterministic, global
            .toList();

    // Then lock, one statement per row, in that order.
    for (String accountId : orderedIds) {
        LedgerAccount row = repository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> notFound(accountId));
        locked.add(row);
    }
    // ...
}
```

Two properties do the work:

1. **The order is global** — ascending by primary key, the same for every
   settlement on the platform.
2. **The order is computed before locking begins** — reading the ids unlocked
   first avoids introducing the opposite problem (locking row 1 to discover its
   id, then discovering it needs row 0).

## Why this actually works

A deadlock requires a cycle in the wait-for graph. If every thread acquires
locks in the same global order, then for any pair of rows `x < y`, a thread
holding `y` can never wait for `x` — it would have to lock `x` first, which
means it does not hold `y` yet.

So for every pair, at most one transaction can be blocked waiting for the other,
and the blocked one always proceeds when the holder commits. **No cycle can
form, so deadlock is impossible** — not improbable, not retried, impossible.

The proof is one line: every transaction's lock set is a prefix-consistent,
ascending sequence, and prefixes of a total order are totally ordered.

## Verification

```java
@Testcontainers(disabledWithoutDocker = true)
@RepeatedTest(5)
void oppositeDirectionTransfersDoNotDeadlock() {
    // two real threads, A→B and B→A, simultaneously
    assertThat(invokeConcurrently(transferAB, transferBA))
            .containsOnly(ok(), ok());
    assertNoDeadlockExceptionWasThrown();
    assertThat(finalBalances).satisfies(exactlyConserved());
}
```

`exactlyConserved()` matters as much as the absence of an exception: it asserts
the total balance across both accounts is unchanged, which catches a partial
settlement that silently succeeded.

## Consequences

- `SELECT … FOR UPDATE` on two rows instead of one, for every settlement. The
  cost is real and it is the right cost: at this concurrency level the ledger
  contention window is microseconds.
- A transaction touching two rows in the "wrong" order is a bug this pattern
  cannot express, which is exactly what a design constraint should do.
- `@Version` is still present on the entity as a second line of defence, but
  pessimistic locking is the effective guarantee.