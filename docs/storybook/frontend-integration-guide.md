# Frontend Integration Guide: Personalized Storybook Platform (Ktab)

This documentation is for frontend engineers implementing the client-side user experience for Ktab's personalized children's storybooks.

---

## Table of Contents
1. [Architecture & Key Highlights](#1-architecture--key-highlights)
2. [Base URL & Authentication](#2-base-url--authentication)
3. [Standard API Envelope](#3-standard-api-envelope)
4. [Complete TypeScript Type Definitions](#4-complete-typescript-type-definitions)
5. [Storybook Lifecycle & State Machine](#5-storybook-lifecycle--state-machine)
6. [API Endpoints Reference](#6-api-endpoints-reference)
   - [6.1 Create Storybook (Single-Request)](#61-create-storybook-single-request)
   - [6.2 Get Storybook Detail / Polling](#62-get-storybook-detail--polling)
   - [6.3 List User Storybooks](#63-list-user-storybooks)
   - [6.4 Edit Story Script and Title (Before Gate 1 Approval)](#64-edit-story-script-and-title-before-gate-1-approval)
   - [6.5 Approve Arabic Story Script](#65-approve-arabic-story-script)
   - [6.6 Approve Character Design Sheet](#66-approve-character-design-sheet)
   - [6.7 Regenerate Character Design Sheet](#67-regenerate-character-design-sheet)
   - [6.8 Regenerate Single Page Illustration](#68-regenerate-single-page-illustration)
   - [6.9 Review Pages Flagged in QA (Owner)](#69-review-pages-flagged-in-qa-owner)
   - [6.10 Get Interactive Reader Manifest](#610-get-interactive-reader-manifest)
   - [6.11 Download Final PDF](#611-download-final-pdf)
   - [6.12 Cancel Storybook Generation](#612-cancel-storybook-generation)
   - [6.13 Child Profiles Management (Optional Standalone CRUD)](#613-child-profiles-management-optional-standalone-crud)
7. [Frontend Code Implementation Examples](#7-frontend-code-implementation-examples)
   - [Option A: `multipart/form-data` with File Objects (Recommended)](#option-a-multipartform-data-with-file-objects-recommended)
   - [Option B: `application/json` with Base64 Data URIs](#option-b-applicationjson-with-base64-data-uris)
8. [Full Payload Examples (All Fields Populated)](#8-full-payload-examples-all-fields-populated)
9. [Validation Matrix & Error Codes](#9-validation-matrix--error-codes)
10. [UI/UX & Integration Recommendations](#10-uiux--integration-recommendations)

---

## 1. Architecture & Key Highlights

* **Single-Request Creation:** The main child profile, companion (pet or sibling), up to 4 supporting characters (grandparents, teachers, friends), creative preferences, and reference photos can all be submitted in **one single HTTP request**. No multi-step creation wizard prerequisites.
* **Dual Transport:** The creation endpoint accepts:
  1. `multipart/form-data` for native file inputs (`childPhoto`, `companionPhoto`, `characterPhotos`).
  2. `application/json` with Base64 data URIs (`data:image/jpeg;base64,...`) for mobile apps or canvas/avatar croppers.
* **1:1 Square & 2K Resolution:** All illustrations are generated at **1:1 square aspect ratio** and **2048 × 2048 px (2K)** resolution. This matches the physical 21 cm × 21 cm hardcover print format with zero cropping.
* **Page Count Constraints:** Storybooks strictly contain between **15 and 20 pages** (inclusive).
* **Privacy & Ephemeral Photo Storage:** Uploaded photos are stripped of EXIF metadata and stored in encrypted S3/Cloudflare R2 vaults (AES-256-GCM). As soon as the character design sheet is generated, the reference photos are **permanently destroyed**.

---

## 2. Base URL & Authentication

* **Base URL:** `http://localhost:8080/api/v1` (or your deployed API gateway environment)
* **Authentication:** All requests require a Bearer token in the `Authorization` header:
  ```http
  Authorization: Bearer <jwt_access_token>
  ```
* **Content Types:**
  - JSON Endpoints: `Content-Type: application/json`
  - Multipart Endpoints: `Content-Type: multipart/form-data` (browser will automatically append boundary parameter)

---

## 3. Standard API Envelope

All backend responses conform to the uniform senior API envelope:

```typescript
export interface ApiResponse<T> {
  success: boolean;                  // true for success, false for business/validation errors
  statusCode: number;               // e.g., 200, 201, 400, 404, 422
  status: string;                   // "OK", "CREATED", "BAD_REQUEST", "UNPROCESSABLE_ENTITY"
  messageStatus: "SUCCESS" | "WARNING" | "ERROR" | "INFO";
  message: string;                  // Localized human-readable message
  correlationId: string;            // MDC correlation/trace ID for logging and bug reports
  timestamp: string;                // ISO-8601 string (e.g. "2026-10-06T08:30:00Z")
  data: T;                          // Response body payload
  errors?: Record<string, string[]>; // Field-level validation errors (if status is 400/422)
}
```

---

## 4. Complete TypeScript Type Definitions

Save these definitions in your project as `types/storybook.ts`:

```typescript
// ============================================================================
// Enums & Domain Literals
// ============================================================================

export type StorybookStatus =
  | "DRAFT"
  | "STORY_READY"
  | "CHARACTER_READY"
  | "ILLUSTRATING"
  | "QA"          // pages the automatic checks could not settle wait for the OWNER (section 6.9)
  | "RENDERING"
  | "READY"       // finished: reader + PDF available
  | "FAILED"
  | "CANCELLED";

// One page the owner must decide about while status === "QA"
export interface FlaggedPage {
  bookId: number;
  pageId: number;
  pageIndex: number;       // use this in the accept/regenerate paths
  generation: number;      // how many times the page has been drawn
  imageUrl: string;        // signed URL, expires - refetch instead of caching
  sceneEn: string;
  problems: string[];      // what the automatic check found (English)
}

export type ArtStyle = "SOFT_WATERCOLOR";

export type Orientation = "SQUARE" | "PORTRAIT";

export type LanguageVariety = "MSA" | "LEBANESE" | "EGYPTIAN" | "GULF";

export type TashkeelLevel = "FULL" | "PARTIAL" | "NONE";

export type ChildGender = "BOY" | "GIRL";

export type AgeBand = "AGE_3_5" | "AGE_6_8" | "AGE_9_10";

export type SkinTone =
  | "VERY_LIGHT"
  | "LIGHT"
  | "LIGHT_OLIVE"
  | "OLIVE"
  | "TAN"
  | "BROWN"
  | "DARK_BROWN";

export type HairColor =
  | "BLACK"
  | "DARK_BROWN"
  | "BROWN"
  | "LIGHT_BROWN"
  | "BLONDE"
  | "RED";

export type HairStyle =
  | "VERY_SHORT"
  | "SHORT_STRAIGHT"
  | "SHORT_CURLY"
  | "MEDIUM_STRAIGHT"
  | "MEDIUM_CURLY"
  | "LONG_STRAIGHT"
  | "LONG_CURLY"
  | "PONYTAIL"
  | "BRAIDS";

export type EyeColor =
  | "DARK_BROWN"
  | "BROWN"
  | "HAZEL"
  | "GREEN"
  | "BLUE"
  | "GREY";

export type CompanionType =
  | "CAT"
  | "DOG"
  | "RABBIT"
  | "PARROT"
  | "BROTHER"
  | "SISTER";

export type PetColor =
  | "WHITE"
  | "BLACK"
  | "GREY"
  | "ORANGE"
  | "BROWN"
  | "BLACK_AND_WHITE"
  | "GREEN";

export type Interest =
  | "FOOTBALL"
  | "CATS"
  | "DOGS"
  | "DINOSAURS"
  | "SPACE"
  | "SEA_CREATURES"
  | "DRAWING"
  | "MUSIC"
  | "CARS"
  | "HORSES"
  | "BOOKS"
  | "COOKING";

export type StorySetting =
  | "BEIRUT"
  | "CAIRO"
  | "RIYADH"
  | "DUBAI"
  | "AMMAN"
  | "GENERIC_CITY"
  | "COUNTRYSIDE";

export type StoryTime =
  | "MORNING"
  | "DAYTIME"
  | "AFTERNOON"
  | "SUNSET"
  | "NIGHT";

export type PageKind =
  | "COVER"
  | "DEDICATION"
  | "STORY"
  | "BACK_COVER";

export type TextZone =
  | "BOTTOM_SPAN"
  | "TOP_SPAN"
  | "LEFT_HALF"
  | "RIGHT_HALF"
  | "NONE";

// ============================================================================
// Child & Character Models
// ============================================================================

export interface ChildAppearance {
  skinTone: SkinTone;
  hairColor?: HairColor;      // Required if hijab is false
  hairStyle?: HairStyle;      // Required if hijab is false
  eyeColor: EyeColor;
  glasses: boolean;
  hijab: boolean;             // Only permitted if gender is GIRL
}

export interface CreateChildProfileRequest {
  nameAr: string;             // 2-30 Arabic characters and spaces
  gender: ChildGender;
  ageBand: AgeBand;
  appearance: ChildAppearance;
  photoBase64?: string;       // Optional Base64 Data URI
}

export interface CompanionSpec {
  type: CompanionType;
  nameAr: string;
  petColor?: PetColor;                 // Required if pet (CAT, DOG, RABBIT, PARROT)
  siblingAppearance?: ChildAppearance; // Required if BROTHER or SISTER
  photoBase64?: string;                // Optional Base64 Data URI
}

export interface CharacterInput {
  id: string;                          // Unique identifier slug (e.g. "grandpa", "mom")
  name: string;                        // Arabic display name (e.g. "الجد سالم")
  type?: "HUMAN" | "ANIMAL" | "FANTASY";
  role?: "MENTOR" | "SUPPORTING" | "FRIEND" | string;
  relationship?: string;               // e.g. "grandfather", "mother", "teacher"
  age?: number;
  gender?: ChildGender;
  clothes?: string;                    // Arabic description of clothing
  appearance?: ChildAppearance;
  personality?: string[];              // e.g. ["حكيم", "صبور"]
  strength?: string;
  weakness?: string;
  favoriteActivity?: string;
  signatureItem?: string;
  speakingStyle?: string;
  photoBase64?: string;                // Optional Base64 Data URI
}

// ============================================================================
// Storybook Request & Response Models
// ============================================================================

export interface CreateStorybookRequest {
  // Child: Provide either child (inline object) OR childProfileId (saved profile)
  child?: CreateChildProfileRequest;
  childProfileId?: number;
  childPhotoBase64?: string;

  // Companion (Optional)
  companion?: CompanionSpec;
  companionPhotoBase64?: string;

  // Supporting Characters (Optional, up to 4)
  characters?: CharacterInput[];

  // Story Configuration
  pageCount: number;                   // Strict integer between 15 and 20
  style: ArtStyle;                     // "SOFT_WATERCOLOR"
  orientation?: Orientation;           // Default: "SQUARE"
  variety?: LanguageVariety;           // Default: "MSA"
  tashkeelLevel?: TashkeelLevel;       // Default: "PARTIAL" (Must be "NONE" if dialect)

  // Creative Tone & Context (Optional)
  interests?: Interest[];              // Maximum 3 interests
  setting?: StorySetting;
  timeOfDay?: StoryTime;
  place?: string;                      // Specific setting (max 120 chars)
  theme?: string;                      // Central theme (e.g., courage, kindness)
  storyTone?: string;                  // Tone of voice (e.g., adventurous, warm)
  lesson?: string;                     // Moral lesson
  storyIdea?: string;                  // Parent's premise / plot seed
  dedication?: string;                 // Book opening dedication (max 300 chars)
  thingsToAvoid?: string[];            // Fears or sensitive topics to omit

  // Legal Consent (Required if any photo is attached)
  photoConsent?: boolean;
}

export interface PageView {
  pageIndex: number;
  kind: PageKind;
  textAr?: string;
  sceneEn?: string;                   // Visual illustration scene prompt in English
  textZone: TextZone;
  imageUrl?: string;
}

export interface StorybookDetail {
  id: number;
  status: StorybookStatus;
  statusMessage?: string;              // Human-friendly localized Arabic status message (e.g. "يجري تأليف وصياغة قصة طفلكم بعناية...")
  titleAr?: string;
  childNameAr?: string;
  variety: LanguageVariety;
  tashkeelLevel: TashkeelLevel;
  pageCount: number;
  dedication?: string;
  pages: PageView[];
  characterSheetUrl?: string;          // Generated 4-panel watercolor character look sheet
  failureReason?: string;              // Present if status === "FAILED"
  lookRegenerationsLeft: number;       // Remaining character look regenerations allowed
  pageRegenerationsLeft: number;       // Remaining page illustration regenerations allowed
}

export interface EditPageRequest {
  pageIndex: number;                   // 0 = cover, 1..N = story pages
  textAr?: string;                     // Updated Arabic narrative text (max 1000 chars)
  sceneEn?: string;                    // Updated English visual scene prompt (max 2000 chars)
}

export interface EditStoryRequest {
  titleAr?: string;                    // Updated Arabic storybook title (2 - 200 chars)
  pages?: EditPageRequest[];           // List of pages to update
}

export interface StorybookSummary {
  id: number;
  titleAr?: string;
  childNameAr?: string;
  status: StorybookStatus;
  pageCount: number;
  coverImageUrl?: string;              // Resolved public / signed URL of the cover page image (if illustrated)
  createdAt: string;                   // ISO timestamp
}

export interface ReaderManifestPage {
  order: number;
  kind: PageKind;
  textAr: string;
  textZone: TextZone;
  imageUrl: string;
}

export interface ReaderManifest {
  bookId: number;
  dir: "rtl" | "ltr";
  titleAr: string;
  pages: ReaderManifestPage[];
}

export interface DownloadUrlResponse {
  url: string;                         // S3 / R2 presigned download URL for print PDF
}

export interface ChildProfileResponse {
  id: number;
  nameAr: string;
  gender: ChildGender;
  ageBand: AgeBand;
  appearance: ChildAppearance;
}
```

---

## 5. Storybook Lifecycle & State Machine

```mermaid
stateDiagram-v2
    [*] --> DRAFT : POST /books
    DRAFT --> STORY_READY : Story generated
    STORY_READY --> CHARACTER_READY : POST /books/{id}/story/approve
    CHARACTER_READY --> CHARACTER_READY : POST /books/{id}/character/regenerate
    CHARACTER_READY --> ILLUSTRATING : POST /books/{id}/character/approve
    ILLUSTRATING --> RENDERING : All 2K illustrations generated
    ILLUSTRATING --> QA : Some pages need the owner's decision
    QA --> RENDERING : Owner accepts every flagged page
    QA --> ILLUSTRATING : Owner redraws a flagged page
    QA --> FAILED : Pipeline error
    RENDERING --> READY : PDF compiled & validated
    READY --> ILLUSTRATING : POST /books/{id}/pages/{idx}/regenerate
    DRAFT --> FAILED : Pipeline error
    STORY_READY --> FAILED : Pipeline error
    CHARACTER_READY --> FAILED : Pipeline error
    ILLUSTRATING --> FAILED : Pipeline error
    RENDERING --> FAILED : Pipeline error
```

### 5.1 Response Messages & UI Display for All Stages

Every response from `GET /books/{id}` and `POST /books` includes a localized Arabic `statusMessage` field directly inside `StorybookDetail`. **Important:** The frontend UI must never display that "AI is developing" or mention artificial intelligence. All user-facing copy reflects a creative storytelling atelier, explicitly notifying parents when emails have been dispatched:

| Stage (`status`) | Backend `statusMessage` | Email Dispatched? | Recommended Frontend UI Display | Next User / Client Action |
|---|---|---|---|---|
| `DRAFT` | `يجري تأليف وصياغة قصة طفلكم بعناية... (أرسلنا تفاصيل الرحلة إلى بريدك الإلكتروني).` | Yes (Roadmap email) | Display creative progress animation + banner: *«تفقد بريدك الإلكتروني لمعرفة خطوات صناعة الكتاب»* | Poll `GET /books/{id}` every 2–3s. |
| `STORY_READY` | `اكتملت صياغة القصة وهي جاهزة لمراجعتكم واعتمادكم (أرسلنا إشعاراً ورابط المراجعة إلى بريدك الإلكتروني).` | Yes (HITL #1 Story email) | Render story script review modal or text reader. Deep link from email routes parent straight here. | Parent clicks **«اعتماد القصة»** (`POST /books/{id}/story/approve`). |
| `CHARACTER_READY` | `لوحة ملامح شخصية طفلكم جاهزة للمعاينة والاعتماد (أرسلنا إشعاراً ورابط المعاينة إلى بريدك الإلكتروني).` | Yes (HITL #2 Look Sheet email) | Display 4-panel watercolor look sheet from `characterSheetUrl`. Deep link from email routes parent straight here. | Parent clicks **«اعتماد الرسم»** (`POST /books/{id}/character/approve`) or **«إعادة الرسم»** (`POST /books/{id}/character/regenerate`). |
| `ILLUSTRATING` | `يجري رسم وتلوين صفحات القصة بالألوان المائية بدقة فائقة...` | No (Internal painting) | Display progress bar: count of illustrated pages (`pages[].imageUrl != null`) out of `pageCount`. | Poll `GET /books/{id}` every 3s. |
| `QA` | `اكتمل رسم الصفحات، وبعضها يحتاج إلى قراركم: اعتمدوا الصورة كما هي أو اطلبوا إعادة رسمها.` | No | Show the flagged pages from `GET /books/{id}/review` (picture + problems) with **«اعتماد»** and **«إعادة الرسم»** buttons per page. | Owner decides each page (section 6.9). |
| `RENDERING` | `يجري تجهيز وتجليد الكتاب النهائي للطباعة والمطالعة...` | No (Internal compilation) | Display book binding / layout compilation spinner. | Poll `GET /books/{id}` every 2–3s. |
| `READY` | `كتاب طفلكم مكتمل وجاهز للقراءة والتصفح والتحميل (تم إرسال روابط القراءة والتحميل إلى بريدك الإلكتروني).` | Yes (Reader & PDF email) | Enable primary **«تصفح الكتاب»** button (`/reader`) and secondary **«تحميل نسخة الطباعة (PDF)»** button (`/download`). | Parent enjoys reading online and downloads the print PDF. |
| `FAILED` | `حدث خطأ أثناء معالجة القصة، يمكنكم إعادة المحاولة.` | No | Display error state with `failureReason` and an active **«استئناف / إعادة المحاولة»** button. | Click **«استئناف»** (`POST /books/{id}/resume`). |
| `CANCELLED` | `تم إلغاء إعداد القصة.` | No | Display cancellation notice. | Parent can start a new book. |

> **No action needed for most pages.** Pages whose only problem is the layout (e.g. scenery drifting into the text area) are accepted automatically and never reach `QA`. A book is in `QA` only when a page has a real problem (wrong character, stray text, anatomy, safety) and has run out of automatic redraws. The `QA` status can be absent entirely for a book; do not build the UI as if it were a required step.

---

### 5.2 Action Endpoint Response Messages (`ApiResponse.message`)

When the frontend triggers lifecycle actions, the backend envelope returns a descriptive Arabic `message` confirming the action and next email touchpoint:

| Action Endpoint | HTTP Status | Response Message (`ApiResponse.message`) |
|---|---|---|
| `POST /books` | `201 CREATED` | `بدأنا برحلة إعداد القصة، وأرسلنا إليك تفاصيل الخطوات القادمة عبر البريد الإلكتروني.` |
| `POST /books/{id}/story/approve` | `202 ACCEPTED` | `تم اعتماد القصة بنجاح وبدأ رسم لوحة الشخصية، وسنرسل لك بريداً إلكترونياً فور جهوزيتها.` |
| `POST /books/{id}/character/approve` | `202 ACCEPTED` | `تم اعتماد رسم الشخصية بنجاح وبدأ رسم صفحات الكتاب، وسنرسل لك بريداً إلكترونياً فور اكتماله.` |
| `POST /books/{id}/character/regenerate` | `202 ACCEPTED` | `جاري إعادة رسم لوحة الشخصية بمظهر جديد.` |
| `POST /books/{id}/pages/{idx}/regenerate` | `202 ACCEPTED` | `جاري إعادة رسم الصفحة المختارة.` |
| `POST /books/{id}/review/pages/{idx}/accept` | `200 OK` | `تم اعتماد صورة الصفحة كما هي.` |
| `POST /books/{id}/review/pages/{idx}/regenerate` | `202 ACCEPTED` | `جاري إعادة رسم الصفحة المختارة.` |
| `POST /books/{id}/photo` | `202 ACCEPTED` | `تم رفع وتشفير الصورة بنجاح.` |
| `POST /books/{id}/resume` | `202 ACCEPTED` | `تم استئناف إعداد القصة بنجاح.` |
| `POST /books/{id}/cancel` | `202 ACCEPTED` | `تم إلغاء إعداد القصة.` |

---

## 6. API Endpoints Reference

### 6.1 Create Storybook (Single-Request)
Creates the book draft, creates child profile if inline, encrypts photos, and launches background generation.

* **Method:** `POST`
* **Path:** `/api/v1/storybook/books`
* **Content-Type:** `multipart/form-data` OR `application/json`
* **Response:** `201 CREATED` with `ApiResponse<StorybookDetail>`

#### Multipart Form Parts:
| Part Name | Type | Description |
|---|---|---|
| `request` | `application/json` (Blob) | **Required.** The JSON stringified `CreateStorybookRequest`. |
| `childPhoto` | Binary (`image/jpeg`, `image/png`) | Optional. Reference photo of the main child. |
| `companionPhoto` | Binary (`image/jpeg`, `image/png`) | Optional. Reference photo of pet or sibling. |
| `characterPhotos` | Binary (`image/jpeg`, `image/png`) | Optional. List/Array of photos for supporting characters. |
| `consent` | `string` / `boolean` | **Required if photos are attached.** Must be `"true"`. |

---

### 6.2 Get Storybook Detail / Polling
Use this endpoint to poll progress or load book status.

* **Method:** `GET`
* **Path:** `/api/v1/storybook/books/{bookId}`
* **Response:** `200 OK` with `ApiResponse<StorybookDetail>` (contains `statusMessage` localized string)

---

### 6.3 List User Storybooks
Returns all books created by the authenticated user.

* **Method:** `GET`
* **Path:** `/api/v1/storybook/books`
* **Response:** `200 OK` with `ApiResponse<StorybookSummary[]>`

---

### 6.4 Edit Story Script and Title (Before Gate 1 Approval)
Allows the parent / user to review and modify the story title and/or page story texts and visual scene prompts while the book is in `STORY_READY` status, prior to approving Gate 1.

* **Method:** `PUT`
* **Path:** `/api/v1/storybook/books/{bookId}/story`
* **Prerequisite Status:** Must be in `STORY_READY` and not yet approved (`storyApprovedAt === null`).
* **Content-Type:** `application/json`
* **Request Body:** `EditStoryRequest`
  ```json
  {
    "titleAr": "مغامرة سامي في الحديقة السحرية",
    "pages": [
      {
        "pageIndex": 1,
        "textAr": "استيقظ سامي مبكراً وخرج إلى الحديقة وهو يبتسم.",
        "sceneEn": "Sami running happily through the green garden under morning sunlight."
      }
    ]
  }
  ```
* **Validation Rules:**
  - `titleAr` (optional): If provided, must be between 2 and 200 characters, non-blank, and pass AI safety moderation.
  - `pages` (optional): List of pages to edit. For each item:
    - `pageIndex` (required): Must match an existing page (0 to pageCount).
    - `textAr` (optional): Arabic text (max 1000 characters). Must not exceed the age-band word limit and must pass moderation.
    - `sceneEn` (optional): English scene prompt for Gemini illustration (max 2000 characters). If editing page 0 (cover), automatically synchronizes cover scene.
  - Child's name spelling is strictly enforced and normalized via `NameEnforcer`.
* **Response:** `200 OK` with `ApiResponse<StorybookDetail>` containing the updated book and page models (`message`: `"تم تحديث نص وعنوان القصة بنجاح."`).

---

### 6.5 Approve Arabic Story Script
Advances the book from `STORY_READY` to character sheet generation.

* **Method:** `POST`
* **Path:** `/api/v1/storybook/books/{bookId}/story/approve`
* **Response:** `202 ACCEPTED` with `ApiResponse<null>` (`message`: `"تم اعتماد القصة بنجاح، وبدأ رسم لوحة الشخصية."`)

---

### 6.6 Approve Character Design Sheet
Advances the book from `CHARACTER_READY` to illustrating the full book pages. Reference photos are permanently purged right after this step.

* **Method:** `POST`
* **Path:** `/api/v1/storybook/books/{bookId}/character/approve`
* **Response:** `202 ACCEPTED` with `ApiResponse<null>` (`message`: `"تم اعتماد رسم الشخصية بنجاح، وبدأ رسم صفحات الكتاب."`)

---

### 6.7 Regenerate Character Design Sheet
Requests a newly painted character look sheet. Decrements `lookRegenerationsLeft`.

* **Method:** `POST`
* **Path:** `/api/v1/storybook/books/{bookId}/character/regenerate`
* **Response:** `202 ACCEPTED` with `ApiResponse<null>` (`message`: `"جاري إعادة رسم لوحة الشخصية بمظهر جديد."`)

---

### 6.8 Regenerate Single Page Illustration
Re-generates the illustration for a specific page without touching other pages.

* **Method:** `POST`
* **Path:** `/api/v1/storybook/books/{bookId}/pages/{pageIndex}/regenerate`
* **Path Parameters:**
  - `pageIndex`: 0-indexed page number (`0` to `pageCount - 1`).
* **Response:** `202 ACCEPTED` with `ApiResponse<null>` (`message`: `"جاري إعادة رسم الصفحة المختارة."`)

---

### 6.9 Review Pages Flagged in QA (Owner)
The automatic checks settle most pages by themselves (a page whose only problem is the layout is accepted automatically). Pages with a real problem (wrong character, stray text, anatomy, safety) wait for the **book's owner**; no admin is involved. Only while `status === "QA"`.

* **List:** `GET /api/v1/storybook/books/{bookId}/review` → `200` with `ApiResponse<FlaggedPage[]>`
  `FlaggedPage = { bookId, pageId, pageIndex, generation, imageUrl (signed), sceneEn, problems: string[] }`
* **Accept as is:** `POST /api/v1/storybook/books/{bookId}/review/pages/{pageIndex}/accept` → `200`. When no page is left waiting the book moves on to the PDF.
* **Draw again:** `POST /api/v1/storybook/books/{bookId}/review/pages/{pageIndex}/regenerate` → `202`. Counts against the book's page-regeneration limit (`400` when used up); the book returns to `ILLUSTRATING`.
* Another user's book answers `404`; a book not in `QA` answers `409` for redraws.
* **Polling:** keep polling `GET /books/{id}` while in `ILLUSTRATING`; stop and show the review UI on `QA`; after a decision go back to polling (`RENDERING` then `READY`).
* **Drawing can take several minutes** when the image service is busy; the backend retries by itself, so do not offer a retry button for that. Offer **«استئناف»** (`POST /books/{id}/resume`) only on `FAILED`.
* Remaining redraws: `pageRegenerationsLeft` in `StorybookDetail` (shared by this flow and 6.8). Disable the redraw button at `0`.

---

### 6.10 Get Interactive Reader Manifest
Returns an RTL-structured manifest designed specifically for web flipping book readers (e.g. `page-flip`, `swiper`, or custom canvas reader).

* **Method:** `GET`
* **Path:** `/api/v1/storybook/books/{bookId}/reader`
* **Response:** `200 OK` with `ApiResponse<ReaderManifest>`

---

### 6.11 Download Final PDF
Fetches a temporary presigned URL to download the high-resolution 300 DPI square print PDF.

* **Method:** `GET`
* **Path:** `/api/v1/storybook/books/{bookId}/download`
* **Response:** `200 OK` with `ApiResponse<{ url: string }>`

---

### 6.12 Cancel Storybook Generation
Stops any ongoing background image or story generation.

* **Method:** `POST`
* **Path:** `/api/v1/storybook/books/{bookId}/cancel`
* **Response:** `202 ACCEPTED` with `ApiResponse<null>`

---

### 6.13 Child Profiles Management (Optional Standalone CRUD)
If the frontend offers a saved "Child Profiles" tab in settings:

* **Create:** `POST /api/v1/storybook/children` (`CreateChildProfileRequest` $\rightarrow$ `ChildProfileResponse`)
* **List:** `GET /api/v1/storybook/children` ($\rightarrow$ `ChildProfileResponse[]`)
* **Update:** `PUT /api/v1/storybook/children/{childId}` (`CreateChildProfileRequest` $\rightarrow$ `ChildProfileResponse`)
* **Delete:** `DELETE /api/v1/storybook/children/{childId}`

---

## 7. Frontend Code Implementation Examples

### Option A: `multipart/form-data` with File Objects (Recommended)

```typescript
import { CreateStorybookRequest, StorybookDetail, ApiResponse } from "./types/storybook";

export async function createStorybookMultipart(
  token: string,
  requestPayload: CreateStorybookRequest,
  files?: {
    childPhoto?: File | null;
    companionPhoto?: File | null;
    characterPhotos?: File[];
  }
): Promise<StorybookDetail> {
  const formData = new FormData();

  // 1. Append the JSON metadata block as an application/json Blob
  const jsonBlob = new Blob([JSON.stringify(requestPayload)], {
    type: "application/json",
  });
  formData.append("request", jsonBlob);

  // 2. Append binary files if provided
  if (files?.childPhoto) {
    formData.append("childPhoto", files.childPhoto);
  }
  if (files?.companionPhoto) {
    formData.append("companionPhoto", files.companionPhoto);
  }
  if (files?.characterPhotos && files.characterPhotos.length > 0) {
    files.characterPhotos.forEach((file) => {
      formData.append("characterPhotos", file);
    });
  }

  // 3. Consent flag (mandatory when photos are attached)
  const hasPhotos = !!(
    files?.childPhoto ||
    files?.companionPhoto ||
    (files?.characterPhotos && files.characterPhotos.length > 0) ||
    requestPayload.childPhotoBase64 ||
    requestPayload.companionPhotoBase64
  );

  if (hasPhotos || requestPayload.photoConsent) {
    formData.append("consent", "true");
  }

  // 4. Send POST request
  // IMPORTANT: Do NOT manually set 'Content-Type' header!
  // The browser will automatically set 'multipart/form-data; boundary=----WebKitFormBoundary...'
  const response = await fetch("/api/v1/storybook/books", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: formData,
  });

  const body: ApiResponse<StorybookDetail> = await response.json();

  if (!response.ok || !body.success) {
    throw new Error(body.message || "Failed to create storybook");
  }

  return body.data;
}
```

---

### Option B: `application/json` with Base64 Data URIs

```typescript
import { CreateStorybookRequest, StorybookDetail, ApiResponse } from "./types/storybook";

export async function createStorybookJson(
  token: string,
  payload: CreateStorybookRequest
): Promise<StorybookDetail> {
  const response = await fetch("/api/v1/storybook/books", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });

  const body: ApiResponse<StorybookDetail> = await response.json();

  if (!response.ok || !body.success) {
    throw new Error(body.message || "Failed to create storybook");
  }

  return body.data;
}
```

---

### Polling Hook / Utility Example

```typescript
export async function pollStorybookStatus(
  token: string,
  bookId: number,
  onUpdate: (detail: StorybookDetail) => void,
  intervalMs = 3000
): Promise<StorybookDetail> {
  return new Promise((resolve, reject) => {
    const timer = setInterval(async () => {
      try {
        const res = await fetch(`/api/v1/storybook/books/${bookId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        const envelope: ApiResponse<StorybookDetail> = await res.json();
        
        if (!res.ok || !envelope.success) {
          clearInterval(timer);
          return reject(new Error(envelope.message));
        }

        const data = envelope.data;
        onUpdate(data);

        // Stop polling on terminal states or states requiring parent action
        if (
          data.status === "READY" ||
          data.status === "QA" ||           // owner must decide, see 6.9
          data.status === "CANCELLED" ||
          data.status === "STORY_READY" ||
          data.status === "CHARACTER_READY" ||
          data.status === "FAILED"
        ) {
          clearInterval(timer);
          resolve(data);
        }
      } catch (err) {
        clearInterval(timer);
        reject(err);
      }
    }, intervalMs);
  });
}
```

---

## 8. Full Payload Examples (All Fields Populated)

### 8.1 Complete JSON Payload with All Fields (Inline Child, Companion, Supporting Character, Base64)

```json
{
  "child": {
    "nameAr": "سامي",
    "gender": "BOY",
    "ageBand": "AGE_6_8",
    "appearance": {
      "skinTone": "OLIVE",
      "hairColor": "BLACK",
      "hairStyle": "SHORT_CURLY",
      "eyeColor": "BROWN",
      "glasses": true,
      "hijab": false
    },
    "photoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD..."
  },
  "companion": {
    "type": "CAT",
    "nameAr": "مشمش",
    "petColor": "ORANGE",
    "photoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD..."
  },
  "characters": [
    {
      "id": "grandpa",
      "name": "الجد سالم",
      "type": "HUMAN",
      "role": "MENTOR",
      "relationship": "grandfather",
      "age": 68,
      "gender": "BOY",
      "clothes": "ثوب أبيض تقليدي وغترة بيضاء ونظارة طبية ذهبية",
      "appearance": {
        "skinTone": "LIGHT_OLIVE",
        "hairColor": "GREY",
        "hairStyle": "SHORT_STRAIGHT",
        "eyeColor": "DARK_BROWN",
        "glasses": true,
        "hijab": false
      },
      "personality": ["حكيم", "صبور", "محب للأشجار والقصص القديمة"],
      "strength": "معرفته العميقة بأسرار الطبيعة والنجوم",
      "weakness": "يمشي ببطء ويحب أخذ قيلولة بعد الظهر",
      "favoriteActivity": "قراءة الكتب القديمة في الحديقة",
      "signatureItem": "بوصلة نحاسية عتيقة ذات نقوش إسلامية",
      "speakingStyle": "يتحدث بصوت هادئ دافئ ويبتسم دائماً",
      "photoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD..."
    }
  ],
  "pageCount": 16,
  "style": "SOFT_WATERCOLOR",
  "orientation": "SQUARE",
  "variety": "MSA",
  "tashkeelLevel": "PARTIAL",
  "interests": ["SPACE", "FOOTBALL", "BOOKS"],
  "setting": "BEIRUT",
  "timeOfDay": "AFTERNOON",
  "place": "في الحديقة العامة الكبرى بجوار المنارة البحرية القديمة",
  "theme": "الشجاعة والتعاون وحب الاستكشاف",
  "storyTone": "مغامرة مرحة مليئة بالدفء والتشويق والأمل",
  "lesson": "التعاون والصبر ومساعدة الكبار واحترام الطبيعة",
  "storyIdea": "سامي والقط مشمش يكتشفان خريطة قديمة تخبئ سر شجرة التوت السحرية",
  "dedication": "إلى بطلنا الصغير سامي، لتبقى شجاعاً وحالماً دائماً",
  "thingsToAvoid": ["الوحوش المخيفة", "الظلام الدامس", "الحشرات المزعجة"],
  "photoConsent": true
}
```

---

### 8.2 Full cURL Command (`multipart/form-data`)

```bash
curl -X POST "http://localhost:8080/api/v1/storybook/books" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1Ni..." \
  -F 'request={
    "child": {
      "nameAr": "سامي",
      "gender": "BOY",
      "ageBand": "AGE_6_8",
      "appearance": {
        "skinTone": "OLIVE",
        "hairColor": "BLACK",
        "hairStyle": "SHORT_CURLY",
        "eyeColor": "BROWN",
        "glasses": true,
        "hijab": false
      }
    },
    "companion": {
      "type": "CAT",
      "nameAr": "مشمش",
      "petColor": "ORANGE"
    },
    "characters": [
      {
        "id": "grandpa",
        "name": "الجد سالم",
        "type": "HUMAN",
        "role": "MENTOR",
        "relationship": "grandfather",
        "age": 68,
        "gender": "BOY",
        "clothes": "ثوب أبيض وغترة بيضاء",
        "personality": ["حكيم", "صبور"]
      }
    ],
    "pageCount": 16,
    "style": "SOFT_WATERCOLOR",
    "orientation": "SQUARE",
    "variety": "MSA",
    "tashkeelLevel": "PARTIAL",
    "interests": ["SPACE", "FOOTBALL"],
    "setting": "BEIRUT",
    "timeOfDay": "AFTERNOON",
    "place": "في الحديقة العامة بجوار المنارة",
    "theme": "الشجاعة والتعاون",
    "storyTone": "مغامرة مرحة",
    "lesson": "التعاون والصبر",
    "storyIdea": "سامي ومشمش يكتشفان بوصلة قديمة في مكتبة الجد سالم",
    "dedication": "إلى بطلنا الصغير سامي",
    "thingsToAvoid": ["الوحوش المخيفة"],
    "photoConsent": true
  };type=application/json' \
  -F 'childPhoto=@/home/user/photos/sami.jpg;type=image/jpeg' \
  -F 'companionPhoto=@/home/user/photos/mishmish.jpg;type=image/jpeg' \
  -F 'characterPhotos=@/home/user/photos/grandpa.jpg;type=image/jpeg' \
  -F 'consent=true'
```

---

## 9. Validation Matrix & Error Codes

| Field | Validation Rule | Error Status | Error Code / Message |
|---|---|---|---|
| `pageCount` | Must be an integer **between 15 and 20** (inclusive) | `400 BAD REQUEST` | `STORYBOOK_INVALID_PAGE_COUNT` |
| `nameAr` | Must match regex `^[\u0621-\u063A\u0641-\u064A\u0671-\u06D3][\u0621-\u063A\u0641-\u064A\u064B-\u0652\u0670\u0671-\u06D3 ]{1,29}$` (Arabic letters and spaces only, 2-30 chars) | `400 BAD REQUEST` | `VALIDATION_FAILED` (`errors.nameAr`) |
| `interests` | Array length between **0 and 3** | `400 BAD REQUEST` | `STORYBOOK_TOO_MANY_INTERESTS` |
| `characters` | Array length between **0 and 4** | `400 BAD REQUEST` | `STORYBOOK_TOO_MANY_CHARACTERS` |
| `variety` & `tashkeelLevel` | If `variety` is dialect (`LEBANESE`, `EGYPTIAN`, `GULF`), `tashkeelLevel` must be `NONE` | `400 BAD REQUEST` | `STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL` |
| `photoConsent` | Must be `true` whenever any photo is uploaded | `400 BAD REQUEST` | `STORYBOOK_PHOTO_CONSENT_REQUIRED` |
| `photo` / `photoBase64` | Allowed MIME types: `image/jpeg`, `image/png`. Max size: 10 MB per file. | `400 BAD REQUEST` | `VALIDATION_FAILED` |
| `style` | Must be `"SOFT_WATERCOLOR"` | `400 BAD REQUEST` | `VALIDATION_FAILED` |
| `orientation` | Must be `"SQUARE"` (or `"PORTRAIT"`) | `400 BAD REQUEST` | `VALIDATION_FAILED` |

---

## 10. UI/UX & Integration Recommendations

1. **Aspect Ratio for Image Containers:**
   Always use CSS `aspect-ratio: 1 / 1;` with `object-fit: cover;` for storybook page illustrations:
   ```css
   .storybook-page-image {
     width: 100%;
     aspect-ratio: 1 / 1;
     object-fit: cover;
     border-radius: 12px;
   }
   ```
2. **Arabic Text Safe Zones:**
   Each page returned in `pages` or reader manifest specifies `textZone` (`BOTTOM_SPAN`, `TOP_SPAN`, `LEFT_HALF`, `RIGHT_HALF`).
   - If `textZone === "BOTTOM_SPAN"`, render a subtle dark gradient at the bottom:
     `background: linear-gradient(to top, rgba(0,0,0,0.7) 0%, rgba(0,0,0,0) 100%);`
     with white, high-contrast Arabic typography.
   - If `textZone === "TOP_SPAN"`, position the text at the top with a top gradient.
3. **Arabic Typography:**
   Use Cairo or Amiri font (`font-family: 'Amiri', 'Cairo', serif;`) with `direction: rtl; text-align: right;` for authentic children's picture book typography.
4. **Photo Cropper & Privacy Notice:**
   - Display a parent consent checkbox: *"I confirm I am the parent or legal guardian and consent to using this photo solely for generating the storybook character."*
   - Inform the parent: *"Photos are encrypted and automatically deleted from our servers once the character design is approved."*

---

## 11. Automated Email Notifications & Frontend Deep Links

The backend automatically dispatches transactional HTML emails to the parent at every major milestone. The frontend routing structure should accommodate these incoming deep links:

| Stage / Milestone | Trigger Event | Email Subject | Action Buttons in Email | Target Frontend Deep-Link Route |
|---|---|---|---|---|
| **1. Storybook Created** | `POST /books` completed | `بدأت رحلة كتاب {child}: ماذا سيحدث الآن؟` | متابعة تقدم الكتاب | `/storybook/books/:id` |
| **2. Story Script Ready (HITL #1)** | `DRAFT` $\rightarrow$ `STORY_READY` | `قصة «{title}» جاهزة لمراجعتك واعتمادك` | قراءة القصة واعتمادها | `/storybook/books/:id` (opens story review modal/tab) |
| **3. Character Look Sheet Ready (HITL #2)** | `STORY_READY` $\rightarrow$ `CHARACTER_READY` | `لوحة رسم شخصية {child} جاهزة للاعتماد` | معاينة واعتماد رسم الشخصية | `/storybook/books/:id` (opens look sheet approval modal) |
| **4. Book Generation Completed** | `RENDERING` $\rightarrow$ `READY` | `تهانينا! كتاب «{title}» مكتمل وجاهز للقراءة الآن` | 1. تصفح في القارئ التفاعلي<br>2. تحميل نسخة الطباعة (PDF) | 1. `/storybook/books/:id/reader`<br>2. `/storybook/books/:id/download` |

> [!NOTE]
> All emails are localized in elegant Arabic, responsive on mobile devices, and respect user privacy by reminding parents about the automatic ephemeral photo purge.
