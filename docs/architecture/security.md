# Security

How Finova defends itself, and — just as importantly — what it does not defend.

## Threat model

The platform handles credentials and money-shaped data. The realistic threats,
in order of damage:

| Threat | Mitigation |
|---|---|
| Credential stuffing / brute force | BCrypt cost factor, generic `INVALID_CREDENTIALS` (no user enumeration), gateway rate limit of 10 req/min on `/api/auth/login`, `LOGIN_FAILED` audit per attempt |
| Token theft | Short-lived access tokens (1h default), refresh tokens stored hashed-id by `jti` and **rotated on every refresh**, per-session revocation on password change and logout |
| Horizontal privilege escalation (customer A reading customer B's account) | Ownership enforced in the service layer via `CurrentUser.checkOwnershipOrAdmin`, never in the controller alone; every list query scoped to the authenticated user id |
| Vertical privilege escalation (customer reaching `/api/fraud/**`) | Enforced twice: gateway `PathAccessPolicy` rejects it before the request leaves the edge, and `@PreAuthorize("hasRole('ADMIN')")` rejects it again in the service |
| Identity spoofing via headers | The gateway **strips every inbound `X-User-*` header** before writing its own from the verified token |
| Duplicate transfers | Unique index on `transaction.idempotency_key`, plus a request fingerprint so a reused key with a different body is rejected |
| Overdraft via race | Pessimistic locking in a global order, plus balance re-validation inside the settlement transaction |
| Money moved but never announced (or the reverse) | Outbox pattern; every event written in the same transaction as the state change |
| Audit tampering | `audit_logs` is append-only — no update path, no delete endpoint. Kafka records arrive through a single consumer with a unique `event_id`, so a replay cannot duplicate a record |
| Secrets in source control | No real secret anywhere. `JWT_SECRET`, `DB_PASSWORD` and the Mongo credentials come from environment variables; `infrastructure/kubernetes/secrets/` contains only a `.example` template |

## Authentication

```
POST /api/auth/register   →  validate, BCrypt-hash, issue a token pair
POST /api/auth/login      →  verify BCrypt, issue a token pair, audit
POST /api/auth/refresh    →  validate the jti, ROTATE the token pair, audit
POST /api/auth/logout     →  revoke the refresh token, audit
```

Tokens are HS256 JWTs signed with a shared secret. Claims: `sub` (user id),
`uid`, `email`, `role`, `typ` (`access` | `refresh`), `jti`, `iss`, `iat`, `exp`.

The **access/refresh distinction is enforced**, not assumed: a refresh token
presented as a bearer token is rejected by the gateway, because the `typ` claim
must be `access`. Getting this wrong is a common and serious bug — a refresh
token typically has a week of lifetime, so accepting it as an access token turns
a stolen long-lived credential into a week of full access.

**Rotation** means each refresh invalidates the previous token. If a refresh
token is stolen and used after the legitimate client has refreshed, the store
already has the new `jti` and the old one fails.

Passwords are BCrypt-hashed with a cost factor that can be raised per
deployment. They are never logged, never returned in a DTO, and never present in
an audit payload. There is a dedicated test asserting the hash does not appear in
any response.

## Authorisation

```
Browser ──JWT──▶ api-gateway ──validates, strips X-User-*, writes identity──▶ service
                   │                                                            │
                   ├─ public paths permitted without a token                     │
                   └─ finova.gateway.admin-paths + any "admin" path segment     │
                      requires ROLE_ADMIN                                        │
                                                                                │
                                          and the service checks again ──────────┘
                                          @PreAuthorize + CurrentUser.checkOwnershipOrAdmin
```

Two layers on purpose. The gateway is the perimeter; the service is the
authority. A service reached directly, or a route the gateway policy does not
know about, is still protected.

**Resource-level authorisation** is the part most systems get wrong. It is not
enough to check "is the caller an admin?" — a customer must also be unable to
read another customer's account, and that check happens on every read:

```java
Account account = repository.findById(id)
        .orElseThrow(() -> notFound(ACCOUNT_NOT_FOUND, "Account", id));
CurrentUser.checkOwnershipOrAdmin(account.getUserId());   // 403 for someone else
```

List queries are scoped rather than filtered after the fact, so a paginated
response can never leak a row that a post-filter would have to remember to hide.

The beneficiary lookup deliberately returns a **masked** number and never the
holder's name. Revealing who an account belongs to is a privacy leak that helps
an attacker target them; the transfer flow needs only enough to confirm the
number is real and in the right currency.

## Input validation

Every request body is Bean-Validated (`@NotBlank`, `@Size`, `@Email`,
`@Pattern`, `@Digits`) and every violation is returned per-field:

```json
{ "code": "VALIDATION_ERROR",
  "details": { "password": "Password must contain at least one uppercase letter" } }
```

Business rules are enforced in the service layer with canonical `ErrorCode`s, not
with ad-hoc messages, so the frontend can map a code to human copy without
parsing English.

Money is always `BigDecimal` at scale 3 via a shared `Money` helper. There is no
`double` anywhere in the money path — binary floating point cannot represent
`0.1`, and a banking ledger that rounds differently on deposit and withdrawal is
a reconciliation nightmare.

SQL injection is structurally prevented by Spring Data JPA parameter binding and
by the admin search being built from a `Specification` rather than string
concatenation.

## Rate limiting

In-memory token bucket at the gateway, keyed by user id (or client IP when
anonymous), with a stricter bucket on `/api/auth/login`. No Redis dependency, so
the platform runs as a single container without infrastructure it does not need.
A multi-replica deployment would move this to Redis so the limit is global rather
than per-pod — noted as a scaling step, not hidden.

Responses carry `Retry-After`.

## What this does NOT protect against

Stated explicitly, because pretending otherwise is worse than admitting it:

1. **No 2FA.** Not implemented. The API returns `twoFactorEnabled: false` and the
   security centre renders "Not enabled" with an explanation. It does not show a
   green tick for a feature that does not exist.
2. **No geolocation.** The security centre shows "Location not provided by
   Finova" with `locationSource: NOT_PROVIDED_BY_BACKEND`. It does not guess a
   city from an IP address.
3. **No PCI scope.** No card data is handled — "card payment" rows in the demo
   data are historical records, not live integrations.
4. **No real money.** Every transfer is simulated. This is stated on the auth
   screens and in the README.
5. **Refresh tokens are stored by `jti`, not hashed.** A database dump yields
   usable `jti` values. Hashing them would be strictly better and is a small
   change; it is listed as a known improvement rather than left implicit.
6. **JWT revocation is not instant.** An access token remains valid until it
   expires (1h) even after logout or a password change. Shortening the TTL or
   adding a revocation list is the trade-off; at 1h with a 1-minute client-side
   refresh, the practical window is small but real.
7. **In-memory rate limiting is per replica.** See above.

## Reporting a vulnerability

This is a portfolio project with no security contact. The honest answer is: do
not run this in production, and do not put real data in it.