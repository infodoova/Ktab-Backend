# Senior Backend Engineering Standards (Java / Spring Boot)

> Always-on rule for this workspace. Every backend code change must adhere to these standards.

---

## API Design

- Follow RESTful conventions: plural nouns, proper HTTP verbs (GET/POST/PUT/PATCH/DELETE)
- Version all APIs via URL path: `/api/v1/`, `/api/v2/` — never break existing versions
  - Define a deprecation policy: minimum support window after notice, `Deprecation`/`Sunset` response headers, changelog entry per version bump
- Return consistent error envelopes, including field-level validation errors:
  ```json
  {
    "status": 400,
    "error": "VALIDATION_ERROR",
    "message": "Validation failed",
    "timestamp": "2026-09-11T12:00:00Z",
    "correlationId": "abc123",
    "errors": { "email": "must be a valid email", "name": "must not be blank" }
  }
  ```
- Validate all inputs at the controller boundary using Bean Validation (`@Valid`, `@NotBlank`)
- Use `@RequestBody` + DTOs, never expose JPA entities directly in API responses
- Use correct status codes: `201 Created`, `204 No Content`, `400` malformed input, `401` unauthenticated, `403` unauthorized, `404` missing resource, `409` conflict/duplicate
- Never expose stack traces or internal exception messages in responses
- Pagination must define sane default/max page sizes; validate sorting and filtering parameters
- Require an `Idempotency-Key` header on POST/PUT endpoints clients may retry (payments, orders)
- Generate OpenAPI/Swagger docs from code annotations (`springdoc-openapi`) — never hand-maintained separately from the implementation

---

## Spring Boot Conventions

- **Controllers**: thin — delegate to a service immediately, no business logic
- **Services**: contain business logic; all `@Transactional` methods must be public and invoked through the Spring-managed bean — calling a `@Transactional` method via `this` bypasses the Spring proxy and silently skips the transaction (no self-invocation); isolating transactional boundaries in a separate class is a practical convention for enforcing this
- **Repositories**: no custom SQL in service layer — encapsulate in `@Repository` methods
- Use constructor injection (not field injection with `@Autowired`)
- Configuration classes in `config.<domain>` subpackage, never in root package

---

## DTO / Mapping

- Separate Request, Response, and domain/entity models — never conflate them
- Never return entities from controllers
- Protect against mass assignment: request DTOs should only expose fields a client is allowed to set
- Prefer MapStruct or explicit mapping for non-trivial models; avoid `BeanUtils.copyProperties()` blindly
- Response DTOs should be immutable where practical (Java records are a good fit)

---

## Security

- Derive identity from the authenticated principal — never trust a client-supplied user/owner ID for authorization decisions
- Explicit authorization check for every protected resource; use Spring Security consistently, with method-level `@PreAuthorize` where URL-pattern rules aren't sufficient
- Never hardcode secrets or API keys — load via environment variables or a secrets manager (Vault, AWS Secrets Manager); nothing sensitive committed to `application.yml`
- Passwords must use a secure adaptive hash (BCrypt or Argon2) — never a fast general-purpose hash
- Enforce HTTPS/TLS in all environments except local dev
- CORS must be explicit per environment — no wildcard `*` origins in production
- Rate-limit public-facing and sensitive endpoints (e.g., Bucket4j)
- Run OWASP Dependency-Check (or Snyk) in CI; block merges on critical/high CVEs
- Short-lived access tokens + refresh tokens, not long-lived static API keys; rotate signing keys on a defined schedule

---

## Transaction Management

- Transaction boundaries belong in the service layer, on public methods
- Use `@Transactional(readOnly = true)` for read operations where it helps
- **Never make external HTTP/API calls while holding a DB transaction open** — it ties up a connection for the call's full duration and is a common cause of pool exhaustion
- Never perform long-running operations inside a transaction
- Be deliberate about isolation level for concurrency-sensitive workflows

---

## Concurrency & Data Integrity

- Back uniqueness constraints with a DB-level `UNIQUE` constraint — don't rely solely on an `existsBy...()` check, which races under concurrent requests
- Use optimistic locking (`@Version`) when concurrent updates are possible; reserve pessimistic locking (`SELECT ... FOR UPDATE`) for high-contention, short-critical-section cases
- Handle duplicate-key / race-condition failures gracefully, not as unhandled 500s
- Make retryable operations explicitly idempotent; use idempotency keys for payment/order-style POSTs

---

## Database

- Every new table or column must have a Flyway migration (`V{n}__description.sql`)
- Never modify an already-applied migration — always create a new one
- No `schema.sql` / `data.sql` for production schema — Flyway only
- Use `@Column(nullable = false)` and DB-level constraints, not just application-level validation
- Always index FK columns and columns used in `WHERE` clauses — deliberately, verified against actual high-volume query patterns rather than indexing everything
- Prefer backward-compatible migrations for zero-downtime deploys: for non-additive changes (drop/rename), deploy code tolerant of both schema states, migrate, then clean up later
- Size the HikariCP pool deliberately against `DB max connections × instance count` — don't leave it at the default

---

## JPA

- Avoid `FetchType.EAGER` — use explicit `JOIN FETCH` or projections instead
- Avoid bidirectional relationships unless actually needed
- Don't use Lombok `@Data` blindly on entities — generated `equals()`/`hashCode()`/`toString()` can trigger lazy-loading recursion or unstable identity semantics
- Be deliberate about entity `equals()`/`hashCode()` — base on a stable business key or ID, not all fields
- Prevent lazy-loaded associations from leaking into serialization (`LazyInitializationException`, or infinite recursion on bidirectional entities) — map to DTOs before returning
- Prefer projections for read-only or high-volume queries

---

## Performance

- Use `Page<T>` for any list endpoint that could return more than 20 rows
- Cache stable reference data with `@Cacheable` + TTL
- Avoid N+1 queries: prefer JPQL `JOIN FETCH`, entity graphs, or Spring Data projections
- Use the application's chosen concurrency model consistently — standard Spring MVC + JDBC/JPA is a blocking, thread-per-request model and that's fine; don't introduce `@Async` or a reactive pipeline just to mask a slow blocking call; reserve `@Async` for genuinely long-running or independent background work; never mix blocking JPA calls into a reactive (WebFlux/R2DBC) pipeline without an explicit boundary

---

## Resilience & Fault Tolerance

- Every HTTP client must define explicit connect and read timeouts — no unbounded waits
- Wrap external service calls in Resilience4j circuit breakers; fail fast rather than cascading
- Define retry policies with exponential backoff for transient failures — never retry a non-idempotent operation without an idempotency key
- Use bulkheads (separate thread pools) so one slow dependency can't starve calls to others
- Validate and map external responses before they reach domain logic — external API DTOs must not become internal domain models (anti-corruption boundary)
- Every critical external dependency needs a tested fallback (cached value, default, graceful error)

---

## Error Handling

- Use `@ControllerAdvice` / `@RestControllerAdvice` for centralized exception mapping
- Never swallow exceptions silently — log with context; reserve `ERROR` for things that need investigation, not expected business failures (a validation failure or "not found" is not an `ERROR`)
- Return 4xx for client errors, 5xx for server errors — never 200 with an error body

---

## Logging & Observability

- Use SLF4J + structured logging: `log.info("Processing page", kv("pageId", id))`
- Generate a correlation/trace ID per request via a filter/interceptor and put it in MDC so it appears in every log line automatically
- Never log passwords, tokens, PII, or full request/response bodies by default
- Avoid INFO-level logging inside large loops or high-volume code paths
- Expose `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`
- Export metrics via Micrometer to Prometheus: latency, error rate, and throughput per endpoint at minimum
- Instrument distributed tracing with OpenTelemetry; propagate trace/span IDs across service boundaries
- Alert on SLO burn rate, not raw error counts
- No service ships "logs-only" — a dashboard must exist before launch

---

## Configuration

- No environment-specific values hardcoded in Java
- Use `@ConfigurationProperties` for grouped settings instead of scattering `@Value`
- Validate required configuration at startup — fail fast if something critical is missing
- Maintain explicit environment profiles
- Never commit production credentials

---

## Testing

- Unit tests for all service and domain logic — test behavior, not implementation details
- Integration tests (`@SpringBootTest`) for controllers and repository layers — prefer smaller test slices (`@WebMvcTest`, `@DataJpaTest`) when sufficient
- Use Testcontainers for DB integration tests — real Postgres in a container, not H2 if production runs Postgres
- Mock external services (`@MockBean`) — never call real APIs in tests; mock at architectural boundaries
- Test validation and error responses, authorization boundaries, and transaction/concurrency-sensitive logic explicitly
- Contract tests (Spring Cloud Contract or Pact) for any API consumed by another team
- Test naming: `methodName_scenario_expectedResult()`

---

## Code Quality

- No generic `catch (Exception e)` except when translating/logging at a boundary
- No magic numbers or strings — use named constants, enums, or value objects for constrained domain values
- Methods should have one clear responsibility
- Avoid boolean method arguments that substantially change behavior — prefer separate methods or an enum
- Favor composition over inheritance
- `Optional` is for return values, not entity fields, DTO fields, or method parameters
- Never return null collections — return empty ones
- Use records for immutable DTOs where appropriate

---

## Time & Money

- Use `Instant` for machine timestamps, `LocalDate` for dates without a timezone
- Never store important timestamps as formatted strings
- Store and compare times consistently — generally UTC
- Use `BigDecimal` for money, never `float`/`double`; define the rounding mode explicitly for financial calculations
- Store currency explicitly whenever amounts may involve multiple currencies

---

## Architecture

- Dependencies point inward: `Controller → Service → Repository / Integration`
- Controllers, persistence details, and external APIs must not dictate business rules
- Don't call `Repository` directly from a `Controller`, or `Service` from a `Repository`
- Keep entities anemic — no calls from an entity into a Spring-managed service
- Don't hide business logic in "utility" classes

---

## Tooling & Code Review

- CI static analysis gate: Checkstyle (style), SpotBugs (bug patterns), SonarQube or equivalent — build fails on new critical/blocker issues
- No merge to main without at least one approving review
- Enforce formatting via Spotless or `google-java-format` in a pre-commit hook / CI check
