# Ktab-Backend — Agent Behavioral Directives

These directives apply to every interaction in this workspace and take precedence over
general defaults.

---

## 1. Actively Implement Senior Backend Standards

When writing, modifying, or reviewing any code in this workspace, you MUST actively
implement — not merely reference — the standards defined in
`.agents/rules/senior-backend.md`.

Every code change you produce must concretely satisfy:

- Controllers are thin; services own all business logic
- `@Transactional` is on public service methods; no self-invocation through `this`
- DTOs separate Request, Response, and domain — entities are never returned from controllers
- Error responses use the consistent envelope with `correlationId` and field-level `errors`
- `@Valid` on all `@RequestBody` parameters; no raw input passes the controller boundary
- No hardcoded secrets; all config loaded via environment variables or secrets manager
- Every schema change has a Flyway migration; never modify an already-applied migration
- No `FetchType.EAGER`; use projections or explicit `JOIN FETCH` for read-only queries
- SLF4J structured logging with MDC correlation ID on every request; no PII in logs
- `ERROR` reserved for operational failures requiring investigation — not expected business failures
- Tests follow `methodName_scenario_expectedResult()` naming with behavior-first assertions
- `BigDecimal` for money, `Instant` for machine timestamps — never `float`/`double` for financials

If a change you are about to make would violate any of these standards, fix the violation
as part of the change — do not defer it.

---

## 2. Packaging Convention

All new Java files must follow the package hierarchy defined in
`.agents/rules/packaging-convention.md`.

- Every new folder must be a subpackage of an existing package
- No classes in the root application package
- Feature code under `features.<domain>`, config under `config.<domain>`

---

## 3. Security Policy

All code changes must comply with `.agents/rules/senior-security.md`. In particular:

- No secrets, tokens, or credentials in any committed file
- Input validated at the controller boundary; HMAC signatures verified before processing webhooks
- `@PreAuthorize` on every endpoint that requires authorization
- Identity derived from the authenticated principal — never from a client-supplied ID
