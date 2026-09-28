# Ktab - Intelligent Reading & Storytelling Platform

Ktab is a cutting-edge backend platform designed to revolutionize the digital reading experience. By fusing traditional e-reading capabilities with advanced Generative AI, Text-to-Speech (TTS), and Interactive Storytelling engines, Ktab offers a multi-modal, immersive environment for readers, authors, and learners.

---

## 🌟 Core Features

### 1. Interactive Storytelling Engine (`com.doova.ktab.interactivestorytelling`)
Transforms static reading into a dynamic, "Choose Your Own Adventure" style experience powered by AI.
*   **Dynamic Narrative Generation**: Uses LLMs (OpenAI/Gemini) to generate story segments on the fly based on user choices.
*   **State Tracking**: Maintains complex session state including:
    *   **Moral Alignment**: Tracks choices on a moral spectrum.
    *   **Political & Psychological States**: Adapts narrative tone based on cumulative decisions.
    *   **Inventory & Survival**: Manages items and health in survival scenarios.
*   **AI Imagery**: Automatically generates contextual illustrations for each scene using **Google Vertex AI (Imagen 3 / Gemini Pro Vision)**.
*   **Visual Styles**: Supports varying artistic styles (e.g., Cyberpunk, Watercolor, Noir) defined in `StoryVisualStyle`.

### 2. Advanced AI Services (`com.doova.ktab.ai`)
A dedicated module for enhancing content consumption and creation.
*   **Book Ending Generator**: Generates alternative endings for uploaded books using **Gemini 1.5 Pro/Flash**, tailored to specific audience profiles (e.g., "Teens 13-16 Dystopian", "Adults Literary").
*   **Smart Summarization**: Produces concise summaries, extracting key themes and "conclusions" from PDF documents using **OpenAI**.
*   **Contextual Analysis**: Analyzes book content to determine genre, tone, and suitability.

### 3. Intelligent OCR Pipeline (`com.doova.ktab.ocr`)
A robust Optical Character Recognition system for processing scanned PDFs and images.
*   **Hybrid Processing**: Combines traditional OCR with AI-driven correction to fix broken words, layout issues, and formatting artifacts.
*   **Structure Preservation**: Maintains original paragraph structure, headings, and lists.
*   **Batch Processing**: asynchronous queue-based processing for large documents.

### 4. Immersive Text-to-Speech (TTS) (`com.doova.ktab.ws`, `service.elevenlabs`)
High-fidelity audio narration for any text content.
*   **ElevenLabs Integration**: Utilizes the ElevenLabs API for ultra-realistic voice synthesis.
*   **Real-Time Streaming**: Streams audio via WebSockets (`ReaderTtsWebSocketHandler`) for instant playback.
*   **Word Alignment**: Provides timestamped word alignment data, allowing the frontend to highlight words in sync with the audio (Karaoke style).

### 5. Comprehensive Library & Reader API (`com.doova.ktab.controller`)
A full-featured REST API for managing the digital library ecosystem.
*   **User Management**: Registration, authentication (JWT), and role-based access control (Reader, Author, Admin).
*   **Library Management**: Personal bookshelves, reading progress tracking, and "Assign to Me" functionality.
*   **Social & Discovery**: Book reviews, ratings, author analytics, and smart recommendations.
*   **Author Tools**: Analytics dashboard for authors to track engagement with their published works.

### 6. Talk to Book — Grounded AI Reading Assistant (`com.doova.ktab.features.talktobook`)
An enterprise-grade conversational AI assistant allowing readers to interactively query books with zero-hallucination guarantees and explicit page citations.
*   **Dual-Routing Hybrid RAG**:
    *   **Pinpoint Retrieval**: Queries specific events, dialogue, and quotes using PostgreSQL Full-Text Search (GIN indexing `tsvector` with `ts_rank` ranking) limiting context to the top 4 relevant pages (~1,200 tokens total).
    *   **Macro Retrieval**: High-level questions (summaries, characters, plot overviews) combine the book Table of Contents (`tbl_book_sections`), introductory pages, and verified web search snippets.
*   **Book Identity Verification**: Autonomously verifies that external web snippets belong strictly to the book and author before injecting into the LLM context.
*   **Strict Modern Standard Arabic**: Enforces answers formulated exclusively in fluent Arabic (العربية الفصحى) with explicit page citations (e.g. `[الصفحة 42]`).
*   **Dual-Tier Semantic Caching**:
    *   *Tier 1*: Exact SHA-256 hash match ($O(1)$ instant lookups).
    *   *Tier 2*: Cosine similarity matching for paraphrased queries ($\ge 0.90$).
    *   *Asynchronous Eviction*: Non-blocking `@Async @EventListener` LFU/LRU cleanup capped at 500 records per book using a PostgreSQL covering index (`INCLUDE (col_id)`).
*   **Multi-Layer Security & Guardrails**:
    *   *Input Validation*: `@NotBlank`, `@Size(min=3, max=350)`, anti-spam regex pattern matching.
    *   *Prompt Injection Defense*: Adversarial heuristics ("ignore previous instructions") + strict XML boundary delimiters (`<instructions>`, `<rules>`, `<context>`, `<main>`).
    *   *Role-Based Security*: Restricted to `@PreAuthorize("hasAnyAuthority('READER')")` using authenticated JWT principals.
    *   *Rate Limiting*: Bucket4j token bucket under `RateLimitTier.AI` (20 RPM per IP) with RFC-compliant `429` & `Retry-After` headers.


---

## 🏗 Project Architecture & Structure

The application follows a modular, domain-driven layered architecture using **Spring Boot 3**.

### 📂 Directory Layout (`src/main/java/com/doova/ktab`)

| Package | Description |
| :--- | :--- |
| **`ai`** | **AI Module**: Contains `config` (Gemini/OpenAI beans), `service` (LLM clients), `prompt` (Prompt engineering templates), and `dto` for AI interactions. |
| **`interactivestorytelling`** | **Story Engine**: Self-contained module with its own `controller`, `service`, `model` (JPA Entities like `Story`, `Turn`), and `image` generation logic. |
| **`ocr`** | **OCR Module**: Handles PDF parsing, text extraction, and correction workflows. |
| **`controller`** | **REST Layer**: Versioned API controllers (`v1`) organized by domain (`auth`, `library`, `reader`, `author`, `configuration`). |
| **`service`** | **Service Layer**: Business logic implementations (`BookService`, `UserService`, `S3Service`, `ReviewService`). |
| **`repository`** | **Data Access**: Spring Data JPA repositories interacting with PostgreSQL. |
| **`dto`** | **Data Transfer Objects**: Organized into `request`, `response`, `event` (e.g., `BookPublishedEvent`), `genre`, and `tts`. |
| **`model`** | **Domain Entities**: Core JPA entities (`User`, `Book`, `ReadingSession`) representing the database schema. |
| **`exception`** | **Error Handling**: Custom exception classes and a global `GlobalExceptionHandler` for standardized API errors. |
| **`security`** | **Security Config**: JWT filters, authentication providers, and security rules. |
| **`ws`** | **WebSockets**: Handlers for real-time features like TTS streaming. |
| **`features.talktobook`** | **Talk to Book Feature**: Conversational AI reading assistant with RAG retriever, semantic caching, LFU/LRU eviction, and guardrails. |
| **`enums`** | **Enumerations**: Categorized into `status` (e.g., `BookStatus`), `user` (`UserRole`), and global types. |
| **`config`** | **Configuration**: App-wide configs for S3, OpenAPI, Async tasks, and WebMvc. |

---

## 🛠 Technology Stack

### Backend Core
*   **Java 21**: Leveraging the latest language features (Records, Pattern Matching, Virtual Threads).
*   **Spring Boot 3.x**: The foundation framework.
*   **Spring Data JPA (Hibernate)**: ORM and database abstraction.
*   **Spring Security**: Robust authentication and authorization with JWT.
*   **Spring WebFlux (Reactor)**: Reactive programming for AI streaming and WebSockets.
*   **Spring AI**: Unified interface for interacting with various AI models (Vertex AI, OpenAI).

### AI & Cloud Services
*   **Google Vertex AI**:
    *   **Gemini 1.5 Pro/Flash**: For complex text generation and reasoning.
    *   **Imagen 3 / Gemini Vision**: For generating scene illustrations.
*   **OpenAI API**: Used for summarization and legacy features.
*   **ElevenLabs API**: Premium Text-to-Speech synthesis.
*   **AWS S3**: Cloud storage for book PDFs, cover images, and generated assets.

### Database & Infrastructure
*   **PostgreSQL**: Relational database.
*   **Docker**: Containerization support.
*   **Maven**: Dependency management and build automation.

---

## 🚀 Getting Started

### Prerequisites
*   JDK 21+
*   Maven 3.8+
*   PostgreSQL Database
*   API Keys for: Google Cloud, OpenAI, ElevenLabs, AWS S3.

### Configuration (`application.properties`)
Ensure the following properties are configured in your `src/main/resources/application.properties` or environment variables:

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

# --- Talk to Book Agent (Isolated Configuration) ---
ktab.talk-to-book.model=${KTAB_TALK_TO_BOOK_MODEL:gpt-5.4}
ktab.talk-to-book.temperature=0.3
ktab.talk-to-book.max-output-tokens=600
ktab.talk-to-book.max-input-tokens=120
ktab.talk-to-book.similarity-threshold=0.90
ktab.talk-to-book.max-records-per-book=500
ktab.talk-to-book.eviction-batch-size=50
ktab.talk-to-book.web-search-enabled=true

# --- ElevenLabs TTS ---
elevenlabs.api-key=your-elevenlabs-key

# --- AWS S3 ---
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

4.  **Access API Documentation**:
    Once running, open Swagger UI to explore endpoints:
    `http://localhost:8080/swagger-ui/index.html`

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
