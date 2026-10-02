# ADR 0002 — Idempotency is a database constraint, not a convention

**Status:** accepted

## Context

A user taps "Confirm transfer" on a flaky connection. The request reaches the
server and settles. The response is lost. The client retries. Now the user has
been charged twice, and they will notice.

This is not a rare edge case. It is the normal behaviour of mobile networks, and
it is the single most damaging class of bug in a payments product.

## Options considered

1. **Check for the key, then insert.** Two concurrent requests both see no row
   and both insert. Classic TOCTOU.
2. **Distributed lock on the key.** Adds a lock service and a failure mode for a
   problem the database already solves.
3. **Unique index + catch the violation.** The database arbitrates.

## Decision

```sql
ALTER TABLE transaction ADD CONSTRAINT uq_transaction_idempotency_key
    UNIQUE (idempotency_key);
```

Three properties fall out of that one line:

**1. A replay returns the original, not an error.**
`POST /api/transactions` with a key already used returns **200** with the
original transaction and an `Idempotent-Replay: true` header. A client retry is
indistinguishable, to the client, from the first response — which is exactly the
point.

**2. The same key with a different payload is rejected.**
A `request_fingerprint` column holds the SHA-256 of the canonical request body.
A key reused with a different amount returns **409 `IDEMPOTENCY_KEY_REUSED`**
rather than silently returning the old transaction, which would hide a client
bug.

**3. Concurrency is safe by construction.**
Two simultaneous requests with the same key race to the unique index. One wins;
the other catches `DataIntegrityViolationException`, reads the winner's row, and
returns it. No lock service, no lease, no timeout.

## The client half

The guarantee only helps if the client behaves. The frontend generates the
idempotency key **once per logical transfer**, at the moment the user leaves the
amount step, and stores it in the transfer draft. Every retry of the final submit
reuses it.

The naive implementation — generate a key per HTTP call — would be useless: the
idempotency guarantee would only ever protect against a duplicate that the client
never produces.

The draft is also mirrored into the URL query so a page refresh mid-flow does not
orphan the key. Navigate away and come back, and the retry is still the same
logical transfer.

## Consequences

- The key is mandatory. A missing or too-short `Idempotency-Key` returns
  `400 VALIDATION_ERROR` rather than being silently ignored.
- Keys are `varchar(80)`, indexed, and retained as long as the transaction. An
  operational cleanup job for very old rows is a future item.
- A malicious client cannot use this to grief others: the key is scoped by
  ownership, and reusing someone else's key returns their own transaction only if
  they also own the sender account — which the ownership check runs against.