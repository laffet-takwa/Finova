# ADR 0001 — The authoritative balance lives in the transaction-service

**Status:** accepted

## Context

A transfer moves money between two accounts. Somebody has to own the balance.

The obvious arrangement is that `account-service` owns `Account` including
`Account.balance`, and `transaction-service` calls it to debit and credit. That
is the first thing anyone builds, and it is wrong for a platform that needs to be
honest about money.

## The problem with the obvious arrangement

A transfer needs two balance writes plus the transaction row. Spread over two
services, that becomes a distributed transaction:

```
transaction-service: BEGIN
  create transaction (PENDING)
  call account-service: debit(sender, amount)     ← committed on its side
  call account-service: credit(receiver, amount)  ← FAILS. connection reset.
  ROLLBACK
```

The debit is already committed in another service. There is no XA here, and
adding it for one business flow is not worth it. The alternatives — saga with
compensation, or two-phase commit between services — turn a five-row operation
into a state machine with retries, timeouts and its own failure modes. The
failure that remains is the hard one: the sender's money left and the receiver's
did not arrive, and only a reconciliation job can find out.

## Decision

Split the responsibility:

| Concern | Owner |
|---|---|
| Holder, account type, currency, status, IBAN | **account-service** |
| Authoritative balance, money movement | **transaction-service** |
| Balance for the UI to read | **account-service** (projection) |

`transaction-service` keeps a `ledger_account` table keyed by the account-service
account id, populated from `account.opened` events and lazily resolved through
Feign when a projection is missing. It also keeps a double-entry `ledger_event`
table recording every DEBIT and CREDIT with balance before and after.

`account-service` keeps its `balance` column as a **read projection**, updated by
a consumer on `transaction.completed`. That consumer is idempotent via a
`balance_projection` marker table written in the same transaction as the update.

## Consequences

**Good**

- A transfer is one local database transaction. All-or-nothing, no compensation,
  no reconciliation job for the common case.
- Pessimistic locking, unique idempotency keys and status guards all work
  naturally, because everything lives in one place.
- No service can move money on its own. Only `transaction-service` holds a
  balance that is authoritative.

**Bad, and honestly so**

- The customer-facing balance is **eventually consistent** — sub-second in
  practice, but eventually. Mitigated by returning both post-settlement balances
  in the transfer response so the UI updates its account store immediately and
  correctly rather than waiting for the projection.
- Two copies of a balance exist, so they can drift. Mitigated by the projection
  consumer being idempotent, and by the transaction-service being the only writer
  of the truth.
- Reading a balance and immediately spending it can be wrong under a race. The
  authoritative check happens at settlement, not at read time, and the settlement
  re-validates — so the worst case is a rejected transfer with a clear
  `INSUFFICIENT_BALANCE`, never an overdraft.
- A projection that never receives its event shows a stale balance forever.
  Accepted: the outbox retries, and the transfer response does not depend on it.

## Why not the alternative

The alternative — keep one balance and accept a saga — is what a system does when
the services are genuinely independent organisations. Here they are one platform
with one schema per service, and correctness of the money path is worth more than
the conceptual purity of a single owning service. The audit ledger still lives in
`user-service`, and accounts still live in `account-service`, so the boundaries
that genuinely matter are preserved.