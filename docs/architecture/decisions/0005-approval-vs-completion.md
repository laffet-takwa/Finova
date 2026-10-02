# ADR 0005 — Approval and completion are separate topics

**Status:** accepted

## Context

The fraud engine decides whether a transfer may proceed. The transaction-service
then moves the money. Two facts, two events.

The specification described the flow as: fraud evaluates, then
"transaction completed/rejected". Implemented literally, the fraud service would
publish `transaction.completed` and the transaction-service would consume it.

## Why that is ambiguous

Both services would then produce *and* consume `transaction.completed`:

```
fraud-service        --transaction.completed-->  transaction-service  (settle)
transaction-service  --transaction.completed-->  account-service      (project)
                                      +------->  notification-service  (notify)
```

A consumer cannot tell which meaning a message carries without inspecting fields
that are not part of the contract. And the notification service — which reacts
to "the money moved" — would be subscribed to a topic that also carries "the risk
engine approved a payment that has not happened yet". A LOW-risk approval would
generate a "Transfer completed" notification for money that never left the
account.

At-least-once delivery makes it worse: a redelivered approval would notify a
customer again, and a redelivered settlement fact would re-apply a balance delta
unless every consumer were idempotent *and* could distinguish the two cases.

## Decision

Give each fact its own topic.

| Topic | Produced by | Means |
|---|---|---|
| `transaction.approved` | fraud-service | *you may proceed* — LOW/MEDIUM risk, or an admin released a flagged alert |
| `transaction.flagged` | fraud-service | *hold* — HIGH risk, funds stay put |
| `transaction.completed` | transaction-service | *the money moved* — balances debited and credited |
| `transaction.failed` | transaction-service | *terminal failure* — no money moved |

Consequences:

- **transaction-service** consumes `transaction.approved` and `transaction.flagged`.
  It does **not** consume its own `transaction.completed` output — there is no
  listener on it, which is verified in the codebase.
- **notification-service** consumes only terminal facts
  (`transaction.completed`, `transaction.failed`, `transaction.flagged`). It has
  no listener on `transaction.approved` and therefore cannot announce a payment
  that has not happened.
- **account-service** consumes only `transaction.completed`, the single source
  of the balance delta.

Now each topic has exactly one producer, one meaning, and a consumer set that
cannot misinterpret it.

## The held-transfer case

The interesting path is an administrator marking a flagged alert "safe". The
fraud service republishes `transaction.approved` for that transaction — the same
event the LOW-risk path produces. The transaction-service's consumer already
handles settlement; it simply arrives later, after a human has looked at it.

That works because the idempotency guards do their job:

1. The original `transaction.created` produced a `transaction.flagged` decision,
   and the transaction is still `PENDING` — settlement has not run.
2. The admin's `transaction.approved` finds `status == PENDING`, locks, settles.
3. The marker table records `(topic, event_id)`, so if either decision is
   redelivered, it is a no-op.

And if the admin confirms fraud instead? The alert becomes `CONFIRMED` and the
funds stay held. The transaction-service has no failure event to react to, so
the transfer remains `FLAGGED`. That is the documented gap in
[`../../backend/EVENT-FLOW.md`](../../backend/EVENT-FLOW.md); closing it needs a
`transaction.rejected` topic, and the confirmation dialog in the UI says the
funds remain held rather than implying an automatic reversal.

## What this costs

One extra topic, and one extra hop for the settlement fact to reach the
notification service (the money has to actually move before you can honestly
tell the customer it did). That is the correct trade: a notification that
over-promises is worse than a notification that arrives 50 ms later.