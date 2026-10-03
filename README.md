# Ktab - Intelligent Reading & Storytelling Platform

Ktab is a cutting-edge backend platform designed to revolutionize the digital reading experience. By fusing traditional e-reading capabilities with advanced Generative AI, Text-to-Speech (TTS), OCR, and Interactive Storytelling engines, Ktab offers a multi-modal, immersive environment for readers, authors, and learners — with a strong focus on Arabic-language content.

---

## 🌟 Core Features

### 1. Book Ingestion & Structuring
Every uploaded PDF is classified and routed to exactly one pipeline that owns its structure extraction, so parsers never collide (`com.doova.ktab.features.ingestion`):
*   **Classification & Routing**: `IngestionRouter`/`RouteResolver` classify a PDF as `DIGITAL`, `SCANNED`, `HYBRID_OCR`, `MIXED`, or `UNKNOWN`, then dispatch it to one of three pipelines based on the classification and feature flags (`KTAB_STUDIO_ENABLED`, `KTAB_OCR_ENABLED`). Admins can override/re-classify.
*   **Native Extraction** (`features.extraction`): Zero-AI-cost pipeline for born-digital PDFs — strips repeated headers/footers and resolves chapter/section structure via embedded outline → printed Arabic فهرس (TOC) → heading detection, with confidence scoring.
*   **OCR Pipeline v3** (`features.ocr`): The pipeline for scanned/hybrid/mixed PDFs — renders pages to images, queues per-page work on **AWS SQS**, calls **Gemini** for page text/structure, harmonizes and stitches pages, detects spreads/orientation/quality issues, and builds the same TOC/section structure under dynamic concurrency quotas.
*   **Studio Pipeline** (`features.studio`): Delegates ingestion to **ElevenLabs Studio**; a Spring Batch pipeline creates/polls/downloads Studio projects and a two-tier sync service (`StudioSyncService`) reconciles status and content, including orphaned-project recovery.

### 2. Interactive Storytelling Engine (`com.doova.ktab.features.story`)
Transforms static reading into a dynamic, "Choose Your Own Adventure" style experience powered by AI.
*   **Dynamic Narrative Generation**: Uses Spring AI (`SpringAiStoryClient`) to generate story turns and choices on the fly.
*   **State Tracking**: Maintains per-session `MoralState`, `PoliticalState`, `PsychologicalState`, and `SurvivalState`, adapting narrative tone and inventory to cumulative decisions.
*   **Scene Canonical Description (SCD)**: Normalizes each scene into a structured, safety-compliant JSON description (subjects, composition, visual anchors) before turning it into an image-generation prompt (`ScdPromptFactory`).

### 3. Ktab AI Storybook (`com.doova.ktab.features.storybook`)
A personalized-publishing product that generates custom, culturally authentic Arabic children's books.
*   **Consistent Child-Hero Illustrations**: Keeps a character visually consistent across every page of a book.
*   **Authentic Arabic Text**: Supports Modern Standard Arabic / dialect and configurable Tashkeel (diacritics) levels, with narrative "Blueprints" and two parent-approval gates before finalizing.
*   **Job Orchestration**: A step-based orchestrator drives LLM calls (Anthropic/OpenAI), Gemini image generation, per-page QA/critic checks, and tracks cost/credits per AI call through a billing ledger.

### 4. AI Video Trailers (`com.doova.ktab.features.trailer`)
Generates promotional video trailers for books via an Anthropic-agent-driven pipeline (`TrailerAgentGateway`) that orchestrates **Higgsfield** video generation and ElevenLabs voice/music, then renders an end-card. Account connection to Higgsfield is handled via OAuth2 + PKCE.

### 5. Advanced AI Services (`com.doova.ktab.features.ai`)
General-purpose generative-AI endpoints not tied to a specific product.
*   **Book Ending Generator**: Generates alternative endings for uploaded books, tailored to specific audience profiles (e.g., "Teens 13-16 Dystopian", "Adults Literary").
*   **Smart Summarization**: Produces streaming summaries and "conclusions" from PDF documents.
*   Backed by Spring AI with both Gemini and OpenAI model configs.

### 6. Image Generation (`com.doova.ktab.features.imagegen`)
Generates and manages illustrative images for books and reader galleries using **Google Gemini image generation** (Vertex AI GenAI SDK) with safety-category filtering, quota/duplicate-in-flight guards, and event-driven async generation. Images are stored in **Cloudflare R2**.

### 7. Text-to-Speech (`com.doova.ktab.features.tts` / `nativetts` / `audiobook`)
Two complementary audio paths, selected by `AudiobookLauncher` based on the `KTAB_STUDIO_ENABLED` flag:
*   **Real-Time Reader TTS** (`features.tts`): A WebSocket handler (`ReaderTtsWebSocketHandler`) streams ElevenLabs audio with word-level timing alignment for synchronized highlighting while reading, using Arabic-aware sentence chunking.
*   **Native Audiobook Generation** (`features.nativetts`): Offline, full-chapter batch synthesis — chunks chapter text without breaking mid-word, calls ElevenLabs' TTS-with-timestamps API per chunk, and stitches the results with FFmpeg.

### 8. Comprehensive Library & Reader API (`com.doova.ktab.controller.v1`)
A full-featured, versioned REST API organized by domain: `auth`, `library`, `reader`, `author`, `publisher`, `librarian`, `admin`, `genre`, `metadata`, `pub` (public), and `internal`.
*   **User Management**: Registration, authentication (JWT), and role-based access control (Reader, Author, Publisher, Librarian, Admin).
*   **Library Management**: Personal bookshelves, reading progress tracking, and book discovery/review.
*   **Author/Publisher Tools**: Analytics for tracking engagement with published works.

### 9. Talk to Book — Grounded AI Reading Assistant (`com.doova.ktab.features.talktobook`)
An enterprise-grade conversational AI assistant allowing readers to interactively query books with zero-hallucination guarantees and explicit page citations.
*   **Dual-Routing Hybrid RAG**:
    *   **Pinpoint Retrieval**: Queries specific events, dialogue, and quotes using PostgreSQL Full-Text Search (GIN indexing `tsvector` with `ts_rank` ranking) limiting context to the top 4 relevant pages (~1,200 tokens total).
    *   **Macro Retrieval**: High-level questions (summaries, characters, plot overviews) combine the book Table of Contents (`tbl_book_sections`), introductory pages, and optional DuckDuckGo-backed web search snippets.
*   **Book Identity Verification**: Autonomously verifies that external web snippets belong strictly to the book and author before injecting into the LLM context.
*   **Strict Modern Standard Arabic**: Enforces answers formulated exclusively in fluent Arabic (العربية الفصحى) with explicit page citations (e.g. `[الصفحة 42]`).
*   **Dual-Tier Semantic Caching**:
    *   *Tier 1*: Exact SHA-256 hash match ($O(1)$ instant lookups).
    *   *Tier 2*: Cosine similarity matching for paraphrased queries ($\ge 0.82$).
    *   *Asynchronous Eviction*: Non-blocking `@Async @EventListener` LFU/LRU cleanup capped at 500 records per book using a PostgreSQL covering index (`INCLUDE (col_id)`).
*   **Multi-Layer Security & Guardrails**:
    *   *Input Validation*: `@NotBlank`, `@Size(min=3, max=350)`, anti-spam regex pattern matching.
    *   *Prompt Injection Defense*: Adversarial heuristics ("ignore previous instructions") + strict XML boundary delimiters (`<instructions>`, `<rules>`, `<context>`, `<main>`).
    *   *Role-Based Security*: Restricted to `@PreAuthorize("hasAnyAuthority('READER')")` using authenticated JWT principals.
    *   *Rate Limiting*: Bucket4j token bucket under `RateLimitTier.AI` (20 RPM per IP) with RFC-compliant `429` & `Retry-After` headers.

---

## 🏗 Project Architecture & Structure

The application follows a modular, domain-driven layered architecture using **Spring Boot 3**, split between shared top-level packages and self-contained feature modules.

### 📂 Top-Level Packages (`src/main/java/com/doova/ktab`)

| Package | Description |
| :--- | :--- |
| **`controller`** | **REST Layer**: Versioned API controllers (`v1`) organized by domain (`auth`, `library`, `reader`, `author`, `publisher`, `librarian`, `admin`, `genre`, `metadata`, `pub`, `internal`). |
| **`service`** | **Service Layer**: Shared business logic implementations (`BookService`, `UserService`, `S3Service`, `ReviewService`). |
| **`repository`** | **Data Access**: Spring Data JPA repositories interacting with PostgreSQL. |
| **`dto`** | **Data Transfer Objects**: Organized into `request`, `response`, `event`, `genre`, and `tts`. |
| **`model`** | **Domain Entities**: Core JPA entities (`User`, `Book`, `ReadingSession`) representing the database schema. |
| **`mappers`** | **MapStruct Mappers**: Entity ↔ DTO conversion. |
| **`exception`** | **Error Handling**: Custom exception classes and a global `GlobalExceptionHandler` for standardized API errors. |
| **`security`** | **Security Config**: JWT filters, authentication providers, and security rules. |
| **`specification`** | **JPA Specifications**: Dynamic query building for search/filter endpoints. |
| **`validation`** | **Custom Validators**: Bean validation annotations beyond the standard set. |
| **`config`** | **Configuration**: App-wide configs for S3, OpenAPI, Async tasks, and WebMvc. |
| **`annotation`**, **`util`**, **`utils`**, **`event`** | Shared annotations, helpers, and application events used across modules. |

### 📂 Feature Modules (`src/main/java/com/doova/ktab/features`)

| Module | Description |
| :--- | :--- |
| **`ingestion`** | Classifies uploaded PDFs and routes each to exactly one of the `extraction` / `ocr` / `studio` pipelines. |
| **`extraction`** | Native, zero-AI-cost structure extraction for born-digital PDFs. |
| **`ocr`** | OCR Engine v3 for scanned/hybrid PDFs — Gemini-powered page OCR over AWS SQS, harmonization, and structure detection. |
| **`studio`** | Integration with ElevenLabs Studio as an alternate ingestion/audiobook backend. |
| **`story`** | Interactive, choice-driven storytelling engine with scene-image generation. |
| **`storybook`** | Ktab AI Storybook — personalized Arabic children's book generation with job orchestration and billing. |
| **`trailer`** | AI-driven video trailer generation (Higgsfield + ElevenLabs), with OAuth2 account connection. |
| **`ai`** | General-purpose AI endpoints: book ending generation, PDF summarization. |
| **`imagegen`** | Gemini-based illustrative image generation, stored in Cloudflare R2. |
| **`tts`** | Real-time, WebSocket-streamed reader TTS with word-level alignment. |
| **`nativetts`** | Offline, batch full-chapter audiobook synthesis via ElevenLabs + FFmpeg. |
| **`audiobook`** | Launcher that picks the Studio or native TTS pipeline per the `KTAB_STUDIO_ENABLED` flag. |
| **`talktobook`** | Grounded, Arabic-only RAG assistant for querying a specific book with page citations. |

---

## 🛠 Technology Stack

### Backend Core
*   **Java 21**: Leveraging the latest language features (Records, Pattern Matching, Virtual Threads).
*   **Spring Boot 3.5**: The foundation framework.
*   **Spring Data JPA (Hibernate)**: ORM and database abstraction.
*   **Spring Security**: Robust authentication and authorization with JWT.
*   **Spring Batch**: Batch processing for ingestion, OCR, TTS, and Studio sync jobs.
*   **Spring AI 1.1**: Unified interface for interacting with various AI models (Vertex AI Gemini, OpenAI).
*   **Flyway**: Database schema migrations.

### AI & Cloud Services
*   **Google Vertex AI / Gemini**: Page OCR, image generation, and general text generation.
*   **OpenAI & Anthropic APIs**: Summarization, Storybook/Trailer agent orchestration.
*   **ElevenLabs**: Both direct TTS (real-time + native batch synthesis) and the managed Studio product.
*   **Higgsfield**: AI video generation for book trailers.
*   **AWS SQS**: Per-page OCR work queueing.
*   **AWS S3 / Cloudflare R2**: Storage for book PDFs, cover images, OCR page renders, and generated assets.
*   **Playwright**: Headless Chromium for PDF rendering support.

### Database & Infrastructure
*   **PostgreSQL**: Relational database, including full-text search (GIN/`tsvector`) for Talk to Book.
*   **Docker / Docker Compose**: Containerization and local orchestration (see `docker-compose.yml`, `Dockerfile`).
*   **Render**: Deployment target (`render.yaml`); see `docs/contabo_deployment_guide.md` for the alternate Contabo VPS deployment path.
*   **Maven**: Dependency management and build automation.
*   **Resilience4j** & **Bucket4j**: Circuit breaking and token-bucket rate limiting.
*   **Micrometer + Prometheus**: Metrics and observability (`/actuator`).

---

## 🚀 Getting Started

### Prerequisites
*   JDK 21+
*   Maven 3.8+
*   PostgreSQL Database
*   API Keys for: Google Cloud (Vertex AI), OpenAI, Anthropic, ElevenLabs, Higgsfield, AWS S3/Cloudflare R2.

### Configuration (`application.properties`)
Ensure the following properties are configured in your `src/main/resources/application.properties` or environment variables (most ship with sensible defaults and can be overridden via env vars, e.g. `KTAB_TALK_TO_BOOK_MODEL`):

```properties
# --- Database ---
spring.datasource.url=jdbc:postgresql://localhost:5432/ktab_db
spring.datasource.username=postgres
spring.datasource.password=your_password

# --- JWT Security ---
application.security.jwt.secret-key=YOUR_VERY_LONG_SECRET_KEY
application.security.jwt.expiration=86400000

# --- Google Vertex AI ---
spring.ai.vertex.ai.gemini.project-id=your-gcp-project-id
spring.ai.vertex.ai.gemini.location=us-central1
# Ensure GOOGLE_APPLICATION_CREDENTIALS env var is set to your JSON key path

# --- OpenAI ---
spring.ai.openai.api-key=sk-your-openai-key

# --- Ingestion routing flags ---
KTAB_STUDIO_ENABLED=false
KTAB_OCR_ENABLED=true

# --- Talk to Book Agent (Isolated Configuration) ---
ktab.talk-to-book.model=${KTAB_TALK_TO_BOOK_MODEL:gpt-5.4}
ktab.talk-to-book.reasoning-effort=none
ktab.talk-to-book.temperature=1.0
ktab.talk-to-book.max-output-tokens=6000
ktab.talk-to-book.max-input-tokens=120
ktab.talk-to-book.similarity-threshold=0.82
ktab.talk-to-book.max-records-per-book=500
ktab.talk-to-book.eviction-batch-size=50
ktab.talk-to-book.web-search-enabled=true

# --- ElevenLabs TTS ---
elevenlabs.api-key=your-elevenlabs-key

# --- AWS S3 / Cloudflare R2 ---
cloud.aws.credentials.access-key=your-access-key
cloud.aws.credentials.secret-key=your-secret-key
cloud.aws.s3.bucket=your-bucket-name
cloud.aws.region.static=us-east-1
```

### 📖 Talk to Book API Quick Reference

| Method | Endpoint | Authorization | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/books/{bookId}/talk` | Bearer Token (`READER`) | Submits a question about a book to the grounded AI assistant. |

#### Request Payload (`TalkToBookRequest`)
```json
{
  "question": "ما هي الفكرة الأساسية التي يدور حولها هذا الكتاب؟"
}
```

#### Validation Rules
* `question`: **Required**, length between **3 and 350 characters**, trimmed, spam-protected (rejects 10+ identical character runs).
* `bookId`: **Required**, must be a positive integer (`@Positive`).
* `Authorization`: Restricted to users with the `READER` role.

#### Success Response Envelope (`ApiResponse<TalkToBookResponse>`)
```json
{
  "success": true,
  "status": 200,
  "message": "تمت الإجابة على السؤال بنجاح",
  "data": {
    "question": "ما هي الفكرة الأساسية التي يدور حولها هذا الكتاب؟",
    "answer": "يدور الكتاب حول رحلة استكشاف الذات... [الصفحة 12]",
    "citedPages": [12, 13],
    "cached": false,
    "source": "INTERNAL_RAG",
    "hitCount": 1
  }
}
```

### Installation & Run

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/doova/ktab.git
    cd ktab
    ```

2.  **Build the project**:
    ```bash
    ./mvnw clean install
    ```

3.  **Run the application**:
    ```bash
    ./mvnw spring-boot:run
    ```

    Or via Docker Compose (brings up PostgreSQL alongside the app):
    ```bash
    docker compose up --build
    ```

4.  **Access API Documentation**:
    Once running, open Swagger UI to explore endpoints:
    `http://localhost:8080/swagger-ui/index.html`

5.  **Health check**:
    `http://localhost:8080/actuator/health`

---

## 📚 Further Documentation

Deeper architecture notes and specs live under `docs/`, including:
*   `docs/ocr_engine_v2.md` / `docs/ocr_engine_v3.md` — OCR pipeline design.
*   `docs/extraction/arabic-book-extraction-spec.md` — native extraction spec.
*   `docs/storybook/` — Ktab AI Storybook product docs (grading guide, launch checklist, client pitch brief).
*   `docs/trailer/` — AI trailer generation launch checklist and directing prompt.
*   `docs/Ktab_Subscription_Entitlement_Architecture_Final.md` — subscription/entitlement model.
*   `docs/contabo_deployment_guide.md` — Contabo VPS deployment guide.

---

## 🤝 Contributing

This project uses a standard Git workflow.
1.  Create a feature branch (`git checkout -b feature/amazing-feature`).
2.  Commit your changes.
3.  Push to the branch.
4.  Open a Pull Request.

---

## 📄 License

[License Information Here]
