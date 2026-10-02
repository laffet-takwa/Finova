# Observability

Finova is observed through one thread that ties a browser click to a ledger row
to a Kibana document: the **correlation id**.

## The correlation id

```
Browser sends:      X-Correlation-Id: 3f1c9a2b-…      (optional; generated if absent)
        │
        ▼
api-gateway         generates if absent, echoes in EVERY response
        │            puts it in the logging MDC, forwards downstream
        ▼
each service         CorrelationIdFilter (servlet) puts it in the MDC,
        │            so every log line is automatically correlated
        ▼
Kafka                DomainEvent.correlationId carries it into the event
        │
        ▼
fraud, notification, account, audit
                     every consumer logs the id from the event envelope
```

Because the id is in the MDC, it appears in **every** log line from a service
without any logging code having to pass it around. That is why
`CorrelationIdFilter` runs at `HIGHEST_PRECEDENCE` — before the Spring Security
chain, so even a 401 is correlated.

It is returned in the `X-Correlation-Id` response header, included in the `ApiError`
envelope, and stored on the `audit_logs` row. An administrator looking at a failed
transfer in the audit log can copy the id and find the entire trace in Kibana.

## Log format

Every service uses the same pattern, so one Logstash grok rule parses all of them:

```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{service},%X{correlationId}] %msg%n
```

producing

```
2026-10-01 14:32:11.482  INFO [transaction-service,3f1c9a2b] Settled TX-20261001-00042
```

Available MDC keys: `correlationId`, `service`, `httpMethod`, `requestPath`,
`responseStatus`, `durationMs`, `userId`, `logger`.

The gateway additionally emits one machine-parseable access line per request:

```
http_request method=POST path=/api/transactions status=201 durationMs=142 userId=3f1c9a2b
```

which is what the Requests and Service Health dashboards aggregate on.

## Logstash pipeline

`infrastructure/elk/logstash/pipeline/logstash.conf`:

1. **Receive** JSON lines over TCP :5000 (the Docker logging driver tags each
   line with its container, so events are attributable without the application
   knowing anything about the pipeline).
2. **Enrich** with `service` from the container name.
3. **Parse** the logback pattern back into `level` and `@timestamp`.
4. **Extract** the gateway access line into `httpMethod`, `requestPath`,
   `httpStatus`, `durationMs`.
5. **Detect domain events** — any message containing `"eventId"` is parsed into an
   `event` object and tagged `finova_domain_event`, which is what the Transaction
   Events and Fraud Events dashboards query.
6. **Normalise** the level to the ECS vocabulary.
7. **Index** into `finova-logs-%{+YYYY.MM.dd}`.

## Kibana dashboards

Import once:

```bash
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@infrastructure/elk/kibana/finova-dashboard.ndjson
```

| Dashboard | Answers |
|---|---|
| **Requests** | Which service is serving traffic, how fast, and what is it returning? |
| **Errors** | What is failing, in which service, and which correlation id to look at? |
| **Transaction Events** | What did the pipeline actually publish, with reference and outcome? |
| **Fraud Events** | What did the risk engine decide, and how is the score distributed? |
| **Service Health** | Per-service request volume, error rate and latency in one view |

## Actuator

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Aggregate health |
| `/actuator/health/readiness` | Gates traffic; Kubernetes readiness probe |
| `/actuator/health/liveness` | Restarts a wedged JVM; Kubernetes liveness probe |
| `/actuator/info` | Build and service metadata |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape target |

Health checks are enabled with `show-details: when-authorized`, so a liveness
probe gets a boolean while an operator gets the detail.

## Tracing a transfer, end to end

The practical exercise. Take a transfer reference, say `TX-20261001-00042`:

1. **Transaction detail** in the UI shows the `correlationId`.
2. **Admin → Audit logs**, filter by that reference. The row carries the
   correlation id and the outcome.
3. **Kibana**, filter
   `correlationId : "3f1c9a2b-…"`. You get, in order:
   - the gateway access line (`POST /api/transactions`, 201, duration)
   - the transaction-service validation and settlement lines
   - the fraud-service scoring line with the risk score
   - the notification-service insert
   - the user-service audit write
4. **Kafka** events carry the same id, so the event stream and the log stream
   agree about which request produced which message.

Without the correlation id this is four separate log searches and a lot of
guessing about timestamps. With it, it is one filter.