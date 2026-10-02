# Finova Backend — Engineering Contract

This document is the single source of truth for how every Finova microservice is written.
Read it completely before creating a file.

## 0. Stack (already pinned by the parent POM — do not change)

| Concern            | Choice                                    |
|--------------------|-------------------------------------------|
| Java               | 17                                        |
| Spring Boot        | 3.3.5                                     |
| Spring Cloud       | 2023.0.3 (Eureka, OpenFeign, Gateway)     |
| Database           | PostgreSQL 16 + Flyway (JPA) / MongoDB 7   |
| Messaging          | Apache Kafka via `spring-kafka`           |
| Auth               | Spring Security 6 + JJWT 0.12.6           |
| Docs               | springdoc-openapi 2.6.0                   |
| Tests              | JUnit 5, Mockito, Spring Boot Test, Testcontainers 1.20.4, spring-kafka-test |
| Mapping            | MapStruct 1.6.2, Lombok 1.18.34           |
| Money              | `BigDecimal`, scale 3 via `com.finova.common.support.Money` |

Group id `com.finova`, version `1.0.0`, parent `com.finova:finova-backend`.

## 1. Ports and service names

| Module                  | `spring.application.name` | Port |
|-------------------------|---------------------------|------|
| discovery-server        | `discovery-server`        | 8761 |
| api-gateway             | `api-gateway`             | 8080 |
| user-service            | `user-service`            | 8081 |
| account-service         | `account-service`         | 8082 |
| transaction-service     | `transaction-service`     | 8083 |
| fraud-service           | `fraud-service`           | 8084 |
| notification-service    | `notification-service`    | 8085 |

## 2. `finova-common` — what is already provided (use it, do not reinvent)

All types live under `com.finova.common`:

- `error.ErrorCode` — canonical error codes; **always** throw `BusinessException(ErrorCode.X)`.
- `error.BusinessException` — `BusinessException(code)`, `(code, message)`, `(code, message, details)`,
  plus `BusinessException.notFound(code, resource, id)`.
- `error.ApiError` — the error envelope. `path` is the request URI.
- `web.CorrelationId` / `web.CorrelationIdFilter` — `OncePerRequestFilter`; register it in every servlet
  service. Echoes `X-Correlation-Id`.
- `web.GlobalExceptionHandler` — `@RestControllerAdvice`; register it in every servlet service.
  Extend it in a service-local subclass if a service needs extra mappings.
- `web.PageResponse<T>` — `PageResponse.from(page)` and `PageResponse.from(page, mapper)`.
- `domain.*` — `Role`, `UserStatus`, `AccountType`, `Currency`, `AccountStatus`, `TransactionType`,
  `TransactionStatus`, `FraudStatus`, `RiskLevel`, `NotificationType`.
- `audit.AuditAction` — the auditable action list.
- `event.Topics` / `event.EventType` / `event.DomainEvent` / `event.TransactionEvent` /
  `event.NotificationRequestEvent` / `event.NotificationCreatedEvent` / `event.AccountBlockedEvent` /
  `event.AuditRecordEvent` — Kafka contracts.
- `security.JwtProperties` / `security.JwtTokenProvider` / `security.JwtAuthenticationFilter` /
  `security.CurrentUser` / `security.AuthenticatedUser` / `security.RestAuthenticationEntryPoint`.
- `support.Money` — `Money.scale(...)`, `Money.requirePositive(...)`, `Money.SCALE = 3`.

### Canonical security wiring for a servlet service

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean JwtTokenProvider jwtTokenProvider(JwtProperties p) { return new JwtTokenProvider(p); }

    @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider provider,
                                          ObjectMapper mapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/**", "/v3/api-docs/**",
                                 "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new RestAuthenticationEntryPoint(mapper)))
            .addFilterBefore(new JwtAuthenticationFilter(provider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

Inside controllers/services use `CurrentUser.userId()`, `CurrentUser.isAdmin()` and
`CurrentUser.checkOwnershipOrAdmin(ownerId)`. Never read the JWT manually.

### Canonical non-web wiring for a servlet service

```java
@Configuration
public class WebSupportConfig {
    @Bean CorrelationIdFilter correlationIdFilter() { return new CorrelationIdFilter(); }
}
```

`CorrelationIdFilter` is a `Filter` bean, so Spring Boot auto-registers it. It also has
`@Order(HIGHEST_PRECEDENCE)`, so it runs before the Spring Security chain.

To read the correlation id inside a service method: inject `HttpServletRequest`, or use
`org.slf4j.MDC.get("correlationId")`. When publishing a Kafka event, pass the correlation id from
`MDC.get(CorrelationId.MDC_KEY)` (falling back to `"unknown"`).

### Canonical Kafka producer wiring

```java
@Component
public class EventPublisher {
    private static final String SERVICE = "transaction-service";
    private final KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, DomainEvent<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public <T> void publish(String topic, EventType type, T payload) {
        DomainEvent<T> event = DomainEvent.of(type, topic, SERVICE,
                MDC.get(CorrelationId.MDC_KEY), payload);
        kafkaTemplate.send(topic, event.transactionId() != null ? String.valueOf(event.transactionId())
                : event.eventId(), event);
    }
}
```

Declare the generic bean explicitly in the application class or a config class so Jackson can
resolve `DomainEvent<?>`. Consumer side uses `JsonDeserializer` with `JavaType` of
`DomainEvent<TransactionEvent>` — see §6.

## 3. Package layout per service

```
com.finova.<service>
├── <Service>Application.java          @SpringBootApplication @EnableFeignClients @EnableConfigurationProperties
├── config/                            SecurityConfig, WebSupportConfig, OpenApiConfig, KafkaTopicConfig, FeignConfig
├── domain/                            JPA entities or Mongo documents + embedded enums if needed
├── repository/                        Spring Data repositories
├── dto/                               request/response records (public API surface)
├── mapper/                            MapStruct interfaces
├── service/                           business logic (@Transactional)
├── controller/                        @RestController, thin
├── client/                            OpenFeign clients to other services
├── messaging/                         producer + @KafkaListener consumers
├── event/                             service-local publisher helpers
├── security/                          service-local security beans if not covered by the standard wiring
├── exception/                         service-local @RestControllerAdvice extends GlobalExceptionHandler
└── config/DataSeeder.java             (dev profile only) realistic demo data
```

**Rules**
- Controllers contain no business logic and never touch repositories.
- Services own transactions (`@Transactional`).
- Repositories return `Optional`; use `orElseThrow` with `BusinessException`.
- Every public service method that reads/writes user-owned data calls
  `CurrentUser.checkOwnershipOrAdmin(ownerId)` **or** relies on a query already scoped by the
  authenticated user id.
- Entities are never returned from controllers — always map to a DTO.
- Use Lombok `@Getter @Setter @NoArgsConstructor` on entities; `@Builder @Getter` on DTOs/records.
  Prefer Java `record`s for DTOs. **No `var`, no comments unless they add real value.**

## 4. Configuration contract

Every service has `src/main/resources/application.yml` with:

```yaml
server:
  port: ${SERVER_PORT:8081}
  shutdown: graceful
spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:finova_users}
    username: ${DB_USER:finova}
    password: ${DB_PASSWORD:finova}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc.time_zone: UTC
        format_sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: finova-<service>
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.finova.common.event.*,com.finova.*"
        spring.json.value.default.type: com.finova.common.event.DomainEvent
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    listener:
      ack-mode: record
      missing-topics-fatal: false
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:http://localhost:8081}
  cloud:
    service-registry:
      eureka:
        client:
          service-url:
            defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
          instance:
            prefer-ip-address: true
        instance:
          health-check-enabled: true
          prefer-ip-address: true
  data.mongodb:
    uri: ${MONGODB_URI:mongodb://localhost:27017/finova_fraud}   # fraud-service only
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  info:
    env:
      enabled: true
info:
  app:
    name: ${spring.application.name}
    description: ...
    version: 1.0.0
finova:
  jwt:
    secret: ${JWT_SECRET:finova-local-development-secret-key-change-me-32bytes}
    issuer: ${JWT_ISSUER:finova}
    access-token-ttl-seconds: ${JWT_ACCESS_TTL:3600}
    refresh-token-ttl-seconds: ${JWT_REFRESH_TTL:604800}
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{correlationId:-}]"
```

`application-dev.yml` activates the demo `DataSeeder`; production uses env-var secrets.
Never hardcode a real secret; the default above is a local-development placeholder only.

Add a `logback-spring.xml` per service using the shared JSON-free pattern with
`service` and `correlationId` in every line so Logstash can parse it. Keep it simple and
identical across services — see `docs/observability/logging-format.md` reference in the
README rather than inventing per-service formats.

## 5. Flyway migrations

`src/main/resources/db/migration/V1__init.sql` per service. Hibernate is set to
`validate`, so **the migration must exactly match the entity mapping**: names, types,
nullability, lengths, unique constraints. Use `uuid` primary keys (`UUID` mapped to
`java.util.UUID` needs a `@Type(type = "uuid-char")` or use `String` ids). Simplest and most
predictable: use `String` ids generated in Java (`UUID.randomUUID().toString()`) and
`varchar(36)` columns. Use `numeric(19,3)` for money. Timestamps as `timestamptz`.

Indexes for every foreign-key column and every column used in a filter.

## 6. Kafka consumers

```java
@Component
public class TransactionEventConsumer {
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = Topics.TRANSACTION_CREATED, groupId = "fraud-service")
    public void onTransactionCreated(ConsumerRecord<String, DomainEvent> record) {
        DomainEvent<TransactionEvent> event = objectMapper.convertValue(record.value(),
                new TypeReference<DomainEvent<TransactionEvent>>() {});
        ...
    }
}
```

- `groupId` must be unique per logical consumer.
- Consume with a **string** value deserializer (`StringDeserializer`) + manual Jackson
  conversion when you need the typed payload. This is the most reliable option and avoids
  `JsonDeserializer` type-mapping surprises. Set in the consumer factory:
  `JsonDeserializer.USE_TYPE_INFO_HEADERS = false` and `VALUE_DEFAULT_TYPE = DomainEvent.class`.
- Handlers must be **idempotent**: check for an already-processed `eventId` or an existing
  document keyed by the business id before writing.
- Wrap the handler in try/catch, log with the correlation id, and never rethrow an
  unrecoverable error (otherwise the consumer loop dies).

## 7. OpenAPI

`config/OpenApiConfig.java`:

```java
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI finovaOpenApi(@Value("${info.app.description}") String description) {
        return new OpenAPI()
            .info(new Info().title("Finova " + serviceTitle)
                .description(description).version("1.0.0")
                .contact(new Contact().name("Finova Engineering").email("engineering@finova.dev")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP)
                    .scheme("bearer").bearerFormat("JWT")));
    }
}
```

Annotate controllers with `@Tag`/`@Operation`/`@ApiResponse`. Add a global
`@SecurityRequirement(name = "bearerAuth")` on the config and mark public auth controllers
with `@SecurityRequirements` (empty) so Swagger UI shows the Authorize button working.

## 8. Tests

- Unit tests: plain JUnit 5 + Mockito, no Spring context. Rule engines, validators,
  services with mocked repositories.
- Slice tests: `@WebMvcTest` for controllers (with `@MockBean` service + `jwt()` request
  post-processor from `spring-security-test`).
- Integration tests: `@SpringBootTest` + Testcontainers. Gate them so the build does not fail
  when Docker is unavailable:
  ```java
  @Testcontainers(disabledWithoutDocker = true)
  ```
  (requires Testcontainers 1.20+, which we have).
- Kafka tests: `@EmbeddedKafka` from `spring-kafka-test` for publish/consume assertions.
- Every test class name ends with `Test`. Methods are named `shouldXxxWhenYyy`.
- No test may depend on execution order or on wall-clock time beyond `Instant.now()` bounds.

## 9. Docker & Kubernetes

Each service gets a multi-stage `Dockerfile` at `infrastructure/docker/<service>/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml ./backend/pom.xml
COPY backend/<module>/pom.xml ./backend/<module>/pom.xml
... (all module poms needed for -pl)
RUN mvn -B -pl <module> -am dependency:go-offline || true
COPY backend ./backend
RUN mvn -B -pl <module> -am package -DskipTests
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S finova && adduser -S finova -G finova
WORKDIR /app
COPY --from=build /build/backend/<module>/target/<module>.jar app.jar
USER finova
EXPOSE <port>
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:<port>/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

(Do **not** create Dockerfiles unless the task explicitly asks — the infrastructure pass owns them.)

## 10. Definition of done for every service

- [ ] Application class, `application.yml`, `application-dev.yml`, `logback-spring.xml`
- [ ] Flyway `V1__init.sql` matching every entity
- [ ] Entities, repositories, DTOs, mappers, services, controllers for every endpoint in the spec
- [ ] Ownership / role authorisation on every endpoint
- [ ] `SecurityConfig`, `WebSupportConfig`, `OpenApiConfig`
- [ ] Error handling through `BusinessException` + `GlobalExceptionHandler`
- [ ] Actuator health/info/metrics
- [ ] Kafka producer and/or consumers, idempotent
- [ ] Audit events published for sensitive operations
- [ ] Unit tests for the business rules; integration tests for the critical paths
- [ ] Swagger annotations on every endpoint
- [ ] Realistic demo `DataSeeder` under the `dev` profile
- [ ] Compiles with `mvn -B -DskipTests package` and `mvn -B test` passes
