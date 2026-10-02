# FINOVA — Architecture

This directory holds the design documents behind the diagrams in the root
`README.md`. Each one explains a decision, not just a picture.

| Document | What it covers |
|---|---|
| [`event-flow.md`](../../backend/EVENT-FLOW.md) | Kafka topology, balance ownership, idempotency, settlement, known gaps |
| [`decisions/0001-balance-ownership.md`](decisions/0001-balance-ownership.md) | Why the ledger lives in the transaction-service |
| [`decisions/0002-idempotency.md`](decisions/0002-idempotency.md) | How duplicate transfers are made impossible |
| [`decisions/0003-outbox.md`](decisions/0003-outbox.md) | Why no service publishes to Kafka inside a business transaction |
| [`decisions/0004-deadlock-freedom.md`](decisions/0004-deadlock-freedom.md) | Global lock ordering instead of retry loops |
| [`decisions/0005-approval-vs-completion.md`](decisions/0005-approval-vs-completion.md) | Why `transaction.approved` and `transaction.completed` are separate topics |
| [`security.md`](security.md) | Threat model, controls, and what is explicitly not protected |
| [`observability.md`](observability.md) | Log format, correlation, ELK pipeline, dashboards |

## The short version

**Service boundaries** follow the data each service owns, not the screens it
serves. `user-service` owns identity and the audit trail. `account-service` owns
the product — who holds what, in which currency, with which status.
`transaction-service` owns the money. `fraud-service` owns risk. The SPA talks to
none of them directly.

**Data ownership** is exclusive. Four PostgreSQL databases and one MongoDB
database; no cross-database query, no shared table, no foreign key between
services. Coordination happens through events, which is why every service can be
deployed, scaled and restarted on its own.

**Consistency** is local. Each service's own data is strongly consistent inside a
database transaction. Across services it is eventually consistent, bounded by a
single event hop, and made safe by idempotent consumers and an outbox.

**Failure** is assumed. The outbox survives a broker outage. Unique indexes
survive redelivery. The lock ordering makes deadlock impossible rather than
retried. The scheduled reconciler recovers a transfer whose decision was lost.