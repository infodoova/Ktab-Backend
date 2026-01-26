# Source Code Index (src/main/java)

## com.doova.ktab

### Core Application
- `KtabApplication.java`: Spring Boot entry point.
- `ServletInitializer.java`: Servlet support.

### Modules

#### `ai` (AI Services)
- `config`: AI model configurations (Gemini, OpenAI).
- `controller`: API endpoints for AI features (Conclusion, Book Ending).
- `service`: Business logic for AI generation.
- `prompt`: Prompt builders and factories.
- `dto`: Request/Response objects for AI.
- `enums`: AI-specific enums.

#### `interactivestorytelling` (Interactive Story Engine)
- `controller`: Story and Session management APIs.
- `service`: Core storytelling logic (Session, Turn, Choice).
- `model`: JPA entities (Story, Turn, ReadingSession) and State records.
- `repository`: Data access layers.
- `image`: Image generation clients (Vertex AI).
- `dto`: Data transfer objects for stories.
- `enums`: Story-specific enums (StoryLens, VisualStyle).

#### `ocr` (OCR Pipeline)
- PDF analysis and text extraction logic.

#### `controller` (REST API)
- `v1`: Version 1 API Controllers.
  - `auth`: Authentication endpoints.
  - `reader`: Book reading and discovery.
  - `author`: Author tools.
  - `library`: User library management.

#### `service` (Business Logic)
- `auth`: Authentication services.
- `book`: Book management services.
- `user`: User management.
- `file`: File storage (S3).
- `elevenlabs`: TTS integration.

#### `dto` (Data Transfer Objects)
- `request`: API request bodies.
- `response`: API response bodies.
- `tts`: TTS specific DTOs.
- `genre`: Genre DTOs.
- `event`: Domain events.

#### `exception` (Error Handling)
- `handler`: `GlobalExceptionHandler`.
- Custom exception classes.

#### `ws` (WebSockets)
- `ReaderTtsWebSocketHandler`: Real-time TTS streaming.

#### `config` (Configuration)
- Security, OpenAPI, Async, etc.

#### `security` (Security)
- JWT filters, Security configuration.

#### `repository` (Data Access)
- JPA repositories for core models.

#### `model` (Domain Models)
- `user`, `book`, `configuration` entities.

#### `enums` (Global Enums)
- `status`: Status enums.
- `user`: User enums.
- Common enums (`ApiMessageKey`, etc).
