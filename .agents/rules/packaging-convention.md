# Project Packaging & Architecture Conventions

This document establishes the mandatory standards for package organization, architectural Separation of Concerns (SoC), Object-Oriented Programming (OOP) principles, and clean coding practices across the Ktab Backend codebase.

---

## 1. Project Structure & Subpackage Enforcement

Every new file (class, record, interface, enum) must strictly respect the project package hierarchy. **Never place any class or folder directly in a root/parent package** (such as `com.doova.ktab` or `com.doova.ktab.controller`). Always place files in the appropriate domain or layer subpackage.

### Package Mapping Matrix

| Layer / Concern | Subpackage Path | Examples / Rules |
|---|---|---|
| **Controllers** | `com.doova.ktab.controller.v1.<domain>` | `controller.v1.admin`, `controller.v1.reader`, `controller.v1.internal` (for webhooks/callbacks), `controller.v1.auth`, `controller.v1.library`. |
| **Service Interfaces** | `com.doova.ktab.service.<domain>` | `service.book.BookService`, `service.file.AttachmentService`. |
| **Service Implementations** | `com.doova.ktab.service.<domain>.impl` | Must end in `*Impl` and reside in the `.impl` subpackage (e.g. `service.book.impl.BookServiceImpl`). |
| **Modular Features** | `com.doova.ktab.features.<feature>.<layer>` | Group self-contained domain features (e.g. `features.ocr.*`, `features.tts.*`, `features.story.*`, `features.ai.*`). Within features, replicate standard subpackages: `.service`, `.service.impl`, `.dto`, `.repository`, `.model`, `.config`. |
| **Data Transfer Objects (DTOs)** | `com.doova.ktab.dto.<domain>` | Request/Response DTOs grouped by domain (`dto.book`, `dto.library`, `dto.review`, `dto.user`). Prefer Java `record`s for immutable payloads. |
| **JPA Entities** | `com.doova.ktab.model.<domain>` | Domain models extending base entity classes (`model.book.Book`, `model.book.BookPage`, `model.user.User`). |
| **Spring Data Repositories** | `com.doova.ktab.repository.<domain>` | `repository.book.BookRepository`, `repository.book.BookPageRepository`. |
| **Mappers** | `com.doova.ktab.mappers.<domain>` | MapStruct mappers (`mappers.book.BookMapper`). |
| **Enums** | `com.doova.ktab.enums.<domain>` | `enums.book`, `enums.status`, `enums.message`, `enums.user`. |
| **Configurations** | `com.doova.ktab.config.<domain>` | Group configs by technology or domain (`config.s3`, `config.qstash`, `config.security`, `config.jpa`, `config.async`). |
| **Exception Handling** | `com.doova.ktab.exception.handler` / `.exception.<domain>` | Keep `@RestControllerAdvice` in `exception.handler`. Domain-specific exceptions live in domain packages. |
| **Events & Listeners** | `com.doova.ktab.event.model` / `com.doova.ktab.event.listener` | Keep event records in `.model` and `@EventListener` classes in `.listener`. |
| **Utilities** | `com.doova.ktab.utils.<domain>` | Stateless helpers grouped by domain (`utils.pagination`, `utils.response`, `utils.security`, `utils.text`, `utils.time`, `utils.validator`). |
| **Test Packages** | `src/test/java/...` | Must exactly mirror the production class package path (e.g., tests for `com.doova.ktab.controller.v1.internal.FaviconController` must be in `com.doova.ktab.controller.v1.internal.FaviconControllerTest`). |

---

## 2. Separation of Concerns (SoC)

Classes must adhere strictly to single-layer responsibility:

### Web / Controller Layer (`controller.v1.*`)
- **Transport Only**: Handle routing, HTTP methods, path variables, query parameters, request validation (`@Valid`), and mapping service responses/outcomes to HTTP status codes (`ResponseEntity`).
- **No Business Logic**: Controllers must NEVER perform direct database operations, execute raw SQL/entity persistence, call external AI APIs, or perform complex JSON serialization.
- **Single Injected Service**: Typically injects a single dedicated interface (e.g., `OcrCallbackController` injects only `OcrCallbackService`).

### Orchestration / Webhook Layer (`service.*` / `features.*.service`)
- Coordinates security checks (HMAC signature verification), DTO transformations, telemetry (Micrometer Timers/Counters), and delegates to domain processors.

### Domain / Processing Layer (`features.*.service.impl` / `service.*.impl`)
- Encapsulates pure business logic, calculations, idempotency checks, entity state mutations, and concurrency throttling.
- **Spring AOP Proxy Preservation**: Always put `@Transactional` or `@Async` on separate service interfaces/implementations. Never call a `@Transactional` method internally from the same class, as this bypasses the Spring AOP proxy.

### Data Access Layer (`repository.*`)
- Responsible solely for persistence queries and entity retrieval. Never leak database/JPA details into the web layer.

---

## 3. Object-Oriented & Clean Code Standards

### SOLID Principles
1. **Single Responsibility (SRP)**: Each class must have one, and only one, reason to change.
2. **Open / Closed (OCP)**: Code against interfaces (`*Service`). Implementations reside in `.impl` and can be swapped or decorated without modifying callers.
3. **Liskov Substitution (LSP)**: Interface implementations must fully conform to the interface contract without unexpected side-effects.
4. **Interface Segregation (ISP)**: Design small, targeted interfaces rather than monolithic multi-purpose interfaces.
5. **Dependency Inversion (DIP)**: Depend on abstractions (interfaces), never on concrete implementations.

### Professional Coding Practices
- **Constructor Injection**: Always use `@RequiredArgsConstructor` (or explicit constructors) with `private final` fields. Never use field injection (`@Autowired`).
- **Immutability**:
  - Use Java `record`s for DTOs, value objects, and message payloads (`OcrPageMessage`).
  - Declare variables as `final` when reassignment is not required.
- **Explicit Result Typing**:
  - Prefer expressive enums or outcome DTOs (e.g. `OcrCallbackResult`) over arbitrary booleans, raw ints, or magic strings.
- **Error Handling**:
  - Catch specific exceptions rather than swallowing generic `Exception` where possible.
  - Signal clear intent: return client-side status (400, 401, 403) for non-retryable issues and server-side status (500) for retryable external failures.
- **Clean Logging**:
  - Use Lombok `@Slf4j`.
  - Use parameterized log statements: `log.info("Processing page {} for book {}", page, bookId)`.
  - Never use `System.out.println()` or `e.printStackTrace()`.
- **Documentation**:
  - Include Javadoc headers on all public interfaces and service methods describing intent, parameters, and return values.
