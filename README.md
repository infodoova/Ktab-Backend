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

# --- ElevenLabs TTS ---
elevenlabs.api-key=your-elevenlabs-key

# --- AWS S3 ---
cloud.aws.credentials.access-key=your-access-key
cloud.aws.credentials.secret-key=your-secret-key
cloud.aws.s3.bucket=your-bucket-name
cloud.aws.region.static=us-east-1
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
