---
inclusion: always
---
---
inclusion: always
---

# Ktab Project Guidelines

## Tech Stack
- Java 21, Spring Boot 3.5.x, Maven
- PostgreSQL with Flyway migrations
- Spring Security with JWT authentication
- Spring AI (OpenAI + Vertex AI Gemini)
- Spring Batch for OCR processing
- AWS S3 for file storage
- Lombok + MapStruct for boilerplate reduction
- SpringDoc OpenAPI for API documentation

## Package Structure
```
com.doova.ktab
├── ai/              # AI integrations (OpenAI, Gemini)
├── annotation/      # Custom annotations (@ApiVersion, @CurrentUser)
├── api/             # REST controllers organized by version (v1, v2)
│   ├── dto/         # Request/Response DTOs
│   ├── validation/  # Validation groups
│   └── version/     # API versioning support
├── config/          # Configuration classes
├── dto/             # Shared DTOs
├── enums/           # Enumerations
├── exceptions/      # Custom exceptions extending KtabException
├── mappers/         # MapStruct mappers
├── model/           # JPA entities
├── ocr/             # OCR batch processing
├── repository/      # Spring Data JPA repositories
├── security/        # Security filters, config, models
├── service/         # Business logic services
└── utils/           # Utility classes
```

## Code Conventions

### Controllers
- Use `@ApiVersion(n)` annotation for versioning
- Use `@RequiredArgsConstructor` for dependency injection
- Return `ResponseEntity<ApiResponse<T>>` using `ResponseUtils.success()`
- Add Swagger annotations (`@Operation`, `@Tag`)
- Inject `MessageSource` for localized messages via `ApiMessageKey`

### Services
- Use `@Service` with `@RequiredArgsConstructor`
- Use `@Transactional` for write operations, `@Transactional(readOnly = true)` for reads
- Throw custom exceptions (`BadRequestException`, `ResourceNotFoundException`) with `ApiMessageKey`

### Entities
- Extend `BaseEntity` for audit fields and ID generation
- Use `tbl_` prefix for table names, `col_` prefix for columns
- Use Lombok annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- Define relationships with proper cascade and orphan removal

### DTOs
- Use records for immutable request/response DTOs when possible
- Use validation annotations (`@NotNull`, `@NotBlank`, etc.)
- Separate request and response DTOs in dedicated packages

### Mappers
- Use MapStruct with `componentModel = "spring"`
- Define as abstract classes when repository injection is needed
- Separate `toEntity()` for create and `updateFromDto()` for updates

### Exception Handling
- Extend `KtabException` for custom exceptions
- Use `ApiMessageKey` enum for message keys
- Messages resolved from `messages.properties`

### API Response Format
- All responses wrapped in `ApiResponse<T>` with: success, statusCode, status, messageStatus, message, timestamp, data

## Testing
- Use `@SpringBootTest` for integration tests
- Test files in `src/test/java` mirroring main structure