### Interactive storytelling module (`interactivestorytelling`)

Location: `src/main/java/com/doova/ktab/interactivestorytelling/domain`

This module implements **author-defined interactive stories** and **AI-driven reading sessions** that progress turn-by-turn with **four choices (A–D)** each turn.

---

### High-level architecture

#### Domain model (JPA entities)

- **`Story`** (`tbl_stories`)
  - **What it is**: Story definition authored by a `User`.
  - **Key fields**: `title`, `genre`, `lens` (`StoryLens`), `constitution` (`StoryConstitution`).
  - **Relations**: `Story (1) -> (many) ReadingSession` via `readingSessions`.
- **`ReadingSession`** (`tbl_reading_sessions`)
  - **What it is**: A reader’s run through a `Story` (the “aggregate root” the service mutates during play).
  - **Key fields**:
    - `status` (`SessionStatus`)
    - `state` (`SessionState`) stored as JSON via `SessionStateConverter`
    - `rollingSummary` (JSON string produced by the summarizer model)
    - `lastSummarizedTurnIndex`
  - **Relations**: `ReadingSession (1) -> (many) Turn` via `turns`.
- **`Turn`** (`tbl_turns`)
  - **What it is**: One generated scene + its 4 choices; later annotated with the chosen choice.
  - **Key fields**: `turnIndex`, `sceneText`, `choicesJson`, `chosenChoiceId`, `summarized`.
  - **Constraints**: unique `(session_id, turn_index)` enforced by `uq_turns_session_turn_index`.

#### Session state machine (immutable state)

- **`SessionState`** is a sealed interface with Jackson polymorphic typing (`@type` discriminator).
- Implementations:
  - **`PoliticalState`**: `visibility`, `moralWeight`, trust metrics.
  - **`PsychologicalState`**: `anxiety`, `attachment` with clamping.
  - **`SurvivalState`**: `stamina`, `hunger` with decay per turn and actions like `rest()` / `scavenge()`.
  - **`MoralState`**: `guilt`, `integrity` with `compromise()` / `uphold()`.
- **`ChoiceEffectResolver`** deterministically maps `(state, choice)` → `nextState` and also advances `turnCount` via `nextTurn()`.
- **`SessionStateFactory`** chooses the initial state based on `StoryLens`.

#### AI integration (Spring AI)

- **`SpringAiStoryClient`** wraps two `ChatClient`s:
  - `interactiveStoryChatClient` → generates the next `GeneratedTurn`
  - `summaryChatClient` → produces `MemorySummary`
- The service expects model responses to be **raw JSON** and parses them with `JsonUtil`.
- **`AiModelProfile`** defines OpenAI options for STORY vs SUMMARY (model currently `gpt-4o` with different temperatures/maxTokens).

#### Prompt + context

- **`PromptFactory`**
  - `storytellerSystem(story, maxSceneWords)`: constitution + strict JSON schema for turn generation.
  - `summarizerSystem(story)`: strict schema for `MemorySummary`.
- **`ContextBuilder`**
  - Builds the user prompts for turn generation and summarization.
  - Provides:
    - `ROLLING_SUMMARY` (previous memory JSON, can be empty)
    - `LAST_RAW_TURNS` (verbatim scenes + chosen choices)
    - `SESSION_STATE` (authoritative JSON)
    - `LAST_CHOICE_ID`

#### Persistence helpers

- **`JsonUtil`**: tolerant JSON parser for common LLM failure modes:
  - strips ``` fences
  - extracts the outermost `{...}`
  - escapes literal newlines inside JSON strings
  - allows trailing commas / unescaped control chars

---

### Runtime flow (end-to-end)

#### 1) Author creates a story

- **Endpoint**: `POST /api/v1/stories`
- **Controller**: `StoryController`
- **Input**: `CreateStoryRequest` which includes `StoryConstitutionDto`
- **Behavior**:
  - Convert DTO → embedded `StoryConstitution`
  - Create `Story(author, title, genre, lens, constitution)`
  - Save via `StoryRepository`

#### 2) Reader starts a session

- **Endpoint**: `POST /api/v1/sessions/start/{storyId}`
- **Controller**: `SessionController`
- **Behavior** (`StorySessionService.startSession`):
  - Load `Story`
  - Pick `initialState` via `SessionStateFactory.initialStateFor(story)`
  - Create `ReadingSession(story, reader, initialState)` and attach it to the story (`story.addSession`)
  - Build prompts:
    - system: `PromptFactory.storytellerSystem(...)`
    - user: `ContextBuilder.buildTurnUserPrompt(rollingSummary=null, lastRawTurns=[], state, lastChoiceId="")`
  - Call AI: `SpringAiStoryClient.generateTurn(...)`
  - Validate output: non-empty sceneText + exactly 4 choices (A–D)
  - Create `Turn(turnIndex=1, sceneText, choicesJson)` and attach to session
  - Persist by saving the aggregate (`storyRepository.save(story)`).

#### 3) Reader chooses A|B|C|D → next turn generated

- **Endpoint**: `POST /api/v1/sessions/{sessionId}/choose`
- **Input**: `ChooseRequest(choiceId)` validated by regex `A|B|C|D`
- **Behavior** (`StorySessionService.chooseAndGenerateNext`):
  - Parse/validate `choiceId` via `ChoiceId.from`
  - Load `ReadingSession`
  - Find latest turn in-memory from `session.getTurns()` and set `chosenChoiceId` (dirty-checked update)
  - Compute next session state via `ChoiceEffectResolver.apply`
  - Build AI context from:
    - rolling summary
    - last N raw turns (default N=2, controlled by `app.story.keepLastRawTurns`)
    - session state
    - last choice id
  - Call AI: `generateTurn`, validate, create next `Turn`, attach to session
  - Periodic summarization (`maybeSummarize`):
    - Every `app.story.summarizeEveryTurns` (default 4) turns, summarize the next batch of turns
    - Update `rollingSummary` and mark summarized turns
  - Persist: `sessionRepo.save(session)`

#### 4) API response mapping

- `SessionController.mapToResponse` parses `choicesJson` into a `Map` and returns `TurnResponse` containing `ChoiceResponse` A–D.

---

### File-by-file index (all files)

#### Controllers / API surface

- **`StoryController.java`**: create and list stories.
- **`SessionController.java`**: start sessions and choose/generate next turns; maps `Turn` → `TurnResponse`.

#### Services / orchestration

- **`StorySessionService.java`**: core orchestration:
  - start session, choose next turn, build prompts/context, validate AI output, persist, summarize periodically.

#### AI / prompts / context / JSON

- **`SpringAiStoryClient.java`**: calls Spring AI `ChatClient` for turn generation and summarization.
- **`PromptFactory.java`**: generates system prompts for storyteller + summarizer.
- **`ContextBuilder.java`**: builds user prompts with rolling memory, last raw turns, session state, and last choice id.
- **`JsonUtil.java`**: tolerant JSON serialize/deserialize + LLM response sanitization.
- **`AiModelProfile.java`**: OpenAI chat options for story vs summary.

#### Entities + embedded value objects

- **`Story.java`**: story definition entity.
- **`StoryConstitution.java`**: embedded constitution fields used in prompts.
- **`ReadingSession.java`**: session aggregate entity.
- **`Turn.java`**: generated scene entity.

#### Repositories

- **`StoryRepository.java`**: `JpaRepository<Story, Long>`.
- **`SessionRepository.java`**: `JpaRepository<ReadingSession, Long>`.
- **`TurnRepository.java`**: turn queries (latest, ranges, etc.).

#### State machine + enums

- **`SessionState.java`**: sealed interface + Jackson subtype mapping.
- **`SessionStateConverter.java`**: JPA converter (SessionState ↔ JSON string).
- **`SessionStateFactory.java`**: selects initial state by `StoryLens`.
- **`ChoiceEffectResolver.java`**: applies choice effects to current state.
- **`PoliticalState.java`**, **`PsychologicalState.java`**, **`SurvivalState.java`**, **`MoralState.java`**: the four state implementations.
- **`StoryLens.java`**: selects which state model governs the story.
- **`ChoiceId.java`**: enum A–D parsing/validation.
- **`SessionStatus.java`**: session lifecycle enum.
- **`Visibility.java`**, **`MoralWeight.java`**: political-state sub-enums.

#### DTOs / records

- **`CreateStoryRequest.java`**: request body for creating stories.
- **`StoryConstitutionDto.java`**: request payload for constitution.
- **`ChooseRequest.java`**: request body for choosing A–D.
- **`GeneratedTurn.java`**: model output schema for a new turn.
- **`MemorySummary.java`**: model output schema for rolling summary.
- **`ChoiceResponse.java`**, **`TurnResponse.java`**: API response types for sessions.
- **`StartSessionRequest.java`**: present but not used by current `SessionController` (start uses path param + `@CurrentUser`).

