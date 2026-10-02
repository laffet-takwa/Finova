# API reference

Per-service OpenAPI documents are generated at runtime and aggregated by the
gateway. This directory holds the human-oriented index and the cross-service
contracts that are not visible in any single service's schema.

## Live endpoints

| What | URL |
|---|---|
| Aggregated Swagger UI | http://localhost:8080/swagger-ui.html |
| Aggregated OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Gateway route table | http://localhost:8080/v3/api-docs/routes |
| User service | http://localhost:8080/docs/user-service/swagger-ui |
| Account service | http://localhost:8080/docs/account-service/swagger-ui |
| Transaction service | http://localhost:8080/docs/transaction-service/swagger-ui |
| Fraud service | http://localhost:8080/docs/fraud-service/swagger-ui |
| Notification service | http://localhost:8080/docs/notification-service/swagger-ui |

## Endpoint index

### `/api/auth/**` → user-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create an account and receive a token pair |
| POST | `/api/auth/login` | public | Exchange credentials for a token pair |
| POST | `/api/auth/refresh` | public | Rotate a refresh token |
| POST | `/api/auth/logout` | bearer | Revoke a refresh token |

### `/api/users/**` → user-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/users/me` | bearer | Current profile |
| PUT | `/api/users/me` | bearer | Update first name, last name, phone |
| POST | `/api/users/me/password` | bearer | Change password, revoke all sessions |
| GET | `/api/users/me/security` | bearer | Security status and recent sign-ins |
| GET | `/api/users/admin` | ADMIN | Paginated user list with filters |
| GET | `/api/users/admin/{id}` | ADMIN | One user |
| PUT | `/api/users/admin/{id}/status` | ADMIN | Block or unblock |
| GET | `/api/users/admin/stats` | ADMIN | Counts and 12-month growth |
| GET | `/api/users/admin/by-email` | ADMIN | Resolve a user by email |
| GET | `/api/users/admin/audit-logs` | ADMIN | Paginated audit trail |

### `/api/accounts/**` → account-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/accounts` | bearer | Open an account |
| GET | `/api/accounts` | bearer | Own accounts (ADMIN: all, filterable) |
| GET | `/api/accounts/{id}` | owner/ADMIN | One account |
| GET | `/api/accounts/{id}/balance` | owner/ADMIN | Balance projection |
| PUT | `/api/accounts/{id}/status` | ADMIN | Block / unblock / close |
| GET | `/api/accounts/lookup` | bearer | Masked beneficiary information |
| GET | `/api/accounts/stats/summary` | ADMIN | Aggregates |
| GET | `/api/accounts/admin/all` | ADMIN | Paginated, filterable |

### `/api/transactions/**` → transaction-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/transactions` | bearer | Create a transfer (`Idempotency-Key` required) |
| GET | `/api/transactions` | bearer | Own transactions, paginated + filtered |
| GET | `/api/transactions/{id}` | holder/ADMIN | One transaction |
| GET | `/api/transactions/{id}/timeline` | holder/ADMIN | Lifecycle steps |
| GET | `/api/transactions/summary` | bearer | Dashboard aggregates |
| GET | `/api/transactions/stats/summary` | ADMIN | Platform aggregates |
| GET | `/api/transactions/admin/all` | ADMIN | Paginated, filterable |

### `/api/fraud/**` → fraud-service — **ADMIN only**

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/fraud/alerts` | Paginated alerts with filters |
| GET | `/api/fraud/alerts/unresolved` | Open + under review |
| GET | `/api/fraud/alerts/{id}` | One alert with timeline |
| GET | `/api/fraud/alerts/transaction/{id}` | Alert for a transaction |
| PATCH | `/api/fraud/alerts/{id}/review` | Start review |
| PATCH | `/api/fraud/alerts/{id}/safe` | Release held funds for settlement |
| PATCH | `/api/fraud/alerts/{id}/confirm` | Confirm as fraudulent (funds stay held) |
| PATCH | `/api/fraud/alerts/{id}/block-account` | Block the sender's account |
| GET | `/api/fraud/stats/summary` | Risk aggregates |

### `/api/notifications/**` → notification-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/notifications` | bearer | Paginated inbox with filters |
| GET | `/api/notifications/unread` | bearer | Newest unread |
| GET | `/api/notifications/unread-count` | bearer | Badge count, grouped by category |
| GET | `/api/notifications/{id}` | bearer | One notification |
| PATCH | `/api/notifications/{id}/read` | bearer | Mark read (idempotent) |
| PATCH | `/api/notifications/read-all` | bearer | Mark all read |
| GET | `/api/notifications/stats/summary` | bearer | Delivery statistics |
| GET | `/api/notifications/preferences` | bearer | Notification preferences |
| PUT | `/api/notifications/preferences` | bearer | Update preferences |
| GET | `/api/notifications/admin/feed` | ADMIN | Cross-user activity feed |

## Cross-service contracts

These are not visible in any single service's schema, because they span services.

### Error envelope

Every service, every endpoint:

```json
{
  "timestamp": "2026-10-01T14:32:11.482Z",
  "status": 422,
  "code": "INSUFFICIENT_BALANCE",
  "message": "Insufficient balance for this transfer.",
  "path": "/api/transactions",
  "correlationId": "3f1c9a2b-7e4d-4a91-b0c3-5d2e8f14a6b0",
  "details": { "amount": "Amount exceeds the available balance" }
}
```

Canonical codes are declared in `com.finova.common.error.ErrorCode`. The frontend
maps each to human copy in `frontend/src/utils/errors.ts`.

### Pagination envelope

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 240,
  "totalPages": 12,
  "first": true,
  "last": false
}
```

`page` is 0-based. `size` defaults to 20 and is capped at 100.

### Event envelope

Every Kafka message:

```json
{
  "eventId": "6b2f…",
  "eventType": "TRANSACTION_APPROVED",
  "topic": "transaction.approved",
  "timestamp": "2026-10-01T14:32:12.004Z",
  "correlationId": "3f1c9a2b-…",
  "sourceService": "fraud-service",
  "payload": { "transactionId": "…", "reference": "TX-20261001-00042", "…": "…" }
}
```

`eventId` gives consumers idempotency; `correlationId` ties the message to the
originating HTTP request.

### Inter-service calls

`fraud-service` → `account-service`:
`PUT /api/accounts/{id}/status` with `{"status":"BLOCKED","reason":"…"}`.
Idempotent — an already-blocked account returns 200 unchanged, so the fraud
review can be retried safely.

`transaction-service` → `account-service`:
`GET /api/accounts/{id}` for sender resolution, and
`GET /api/accounts?accountNumber=…` for receiver resolution (the
`/lookup` endpoint deliberately withholds the account id and the holder's user
id). Any Feign failure maps to `503 SERVICE_UNAVAILABLE` with a message telling
the user to retry — never a silent partial transfer.

Both forward `Authorization` and `X-Correlation-Id`.

## Authentication in Swagger UI

1. `POST /api/auth/login` with a seeded account, or use Swagger's **Authorize**
   dialog to paste the `accessToken` from the login response.
2. The gateway's aggregated document declares the `bearerAuth` security scheme,
   so the Authorize button works on every proxied service document too.