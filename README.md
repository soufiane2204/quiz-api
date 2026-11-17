# Quiz API

A Spring Boot REST API that uses Spring AI and Anthropic Claude to generate multiple-choice quizzes on any topic, stores them in MySQL, and scores your answers.

## Features

- Generate a quiz with AI from a topic, a difficulty (`EASY`, `MEDIUM`, `HARD`) and a number of questions (1-10).
- Each question has exactly 4 options and one correct answer.
- Correct answers are hidden until you submit your answers.
- Submitting returns your score and a correction for every question, with an explanation.
- Every attempt is saved, so you can list previous scores.
- AI output is checked before it is saved. Bad output returns HTTP 502.
- All errors are returned as JSON in one consistent format.

## Tech stack

| Component | Version / detail |
|---|---|
| Java | 17 |
| Spring Boot | 4.1.2 (Web MVC, Data JPA, Validation) |
| Spring AI | 2.0.1 (`spring-ai-starter-model-anthropic`) |
| AI model | `claude-sonnet-5-5` (set in `spring.ai.anthropic.chat.options.model`, max tokens 16000) |
| Database | MySQL 8 (runtime), H2 in-memory (tests only) |
| Build | Maven (wrapper included) |
| Other | Lombok, Java records for DTOs |
| Tests | JUnit 5, Mockito, MockMvc |

## How to run

### Prerequisites

- Java 17
- MySQL 8 running on `localhost:3306`
- An Anthropic API key

### 1. Create the database

```sql
CREATE DATABASE quiz_db;
```

Tables are created automatically on startup (`spring.jpa.hibernate.ddl-auto=update`).

### 2. Set the environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_USERNAME` | no | `root` | MySQL user |
| `DB_PASSWORD` | yes | none | MySQL password |
| `ANTHROPIC_API_KEY` | yes (the app does not start without it) | none | Claude API key |

You can set them in your shell:

```bash
# Linux / macOS
export DB_USERNAME=root
export DB_PASSWORD=your-db-password
export ANTHROPIC_API_KEY=your-anthropic-api-key
```

```powershell
# Windows PowerShell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-db-password"
$env:ANTHROPIC_API_KEY="your-anthropic-api-key"
```

Or use an optional `.env` file in the project root. It is loaded by
`spring.config.import=optional:file:.env[.properties]`, so it uses the properties format:

```properties
DB_USERNAME=root
DB_PASSWORD=your-db-password
ANTHROPIC_API_KEY=your-anthropic-api-key
```

> **Never commit the `.env` file.** It holds real secrets. It is already listed in `.gitignore`.

### 3. Start the app

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
mvnw.cmd spring-boot:run
```

The API runs on **http://localhost:8084**.

### Optional: run with a free local model (Ollama)

Instead of Claude you can use a local model through [Ollama](https://ollama.com). No API key or credit is needed.

1. Install Ollama and pull the model:
   ```powershell
   ollama pull qwen2.5:7b
   ```
2. Start the app with the `ollama` profile:
   ```powershell
   ./mvnw spring-boot:run "-Dspring-boot.run.profiles=ollama"
   ```

The settings are in `src/main/resources/application-ollama.properties`. The property `spring.ai.model.chat`
chooses the active provider (`anthropic` by default, `ollama` with this profile).
Local models are slower and return invalid quizzes more often. When that happens the API answers HTTP 502, so just try again.

## API endpoints

Base path: `/api/quizzes`

| Method | Path | Body | Success | Description |
|---|---|---|---|---|
| POST | `/api/quizzes/generate` | `GenerateQuizRequest` | 201 | AI generates and saves a quiz. Returned **without** correct answers. |
| GET | `/api/quizzes` | none | 200 | List all quizzes (summary with question count). |
| GET | `/api/quizzes/{id}` | none | 200 | One quiz **without** correct answers. |
| POST | `/api/quizzes/{id}/submit` | `SubmitAnswersRequest` | 200 | Score the answers. Returns score and correction. |
| GET | `/api/quizzes/{id}/attempts` | none | 200 | List previous attempts, newest first. |

> Correct answers (`correctIndex`) and explanations are **only** revealed in the response of
> `POST /api/quizzes/{id}/submit`. The generate and get endpoints never return them.

### Generate a quiz

```bash
curl -X POST http://localhost:8084/api/quizzes/generate \
  -H "Content-Type: application/json" \
  -d '{"topic": "Java streams", "difficulty": "EASY", "numberOfQuestions": 2}'
```

Response `201 Created`:

```json
{
  "id": 1,
  "topic": "Java streams",
  "difficulty": "EASY",
  "createdAt": "2026-11-16T10:15:30.123Z",
  "questions": [
    {
      "id": 1,
      "text": "Which method turns a List into a Stream?",
      "options": ["list.toStream()", "list.stream()", "Stream.from(list)", "list.iterator()"]
    },
    {
      "id": 2,
      "text": "Which operation is a terminal operation?",
      "options": ["map", "filter", "collect", "peek"]
    }
  ]
}
```

### List all quizzes

```bash
curl http://localhost:8084/api/quizzes
```

Response `200 OK`:

```json
[
  {
    "id": 1,
    "topic": "Java streams",
    "difficulty": "EASY",
    "createdAt": "2026-11-16T10:15:30.123Z",
    "questionCount": 2
  }
]
```

### Get one quiz

```bash
curl http://localhost:8084/api/quizzes/1
```

Response `200 OK` (same shape as the generate response, still without answers):

```json
{
  "id": 1,
  "topic": "Java streams",
  "difficulty": "EASY",
  "createdAt": "2026-11-16T10:15:30.123Z",
  "questions": [
    {
      "id": 1,
      "text": "Which method turns a List into a Stream?",
      "options": ["list.toStream()", "list.stream()", "Stream.from(list)", "list.iterator()"]
    },
    {
      "id": 2,
      "text": "Which operation is a terminal operation?",
      "options": ["map", "filter", "collect", "peek"]
    }
  ]
}
```

### Submit answers

Send one option index (0-3) per question, in question order.

```bash
curl -X POST http://localhost:8084/api/quizzes/1/submit \
  -H "Content-Type: application/json" \
  -d '{"answers": [1, 0]}'
```

Response `200 OK`:

```json
{
  "attemptId": 1,
  "quizId": 1,
  "score": 1,
  "totalQuestions": 2,
  "corrections": [
    {
      "questionId": 1,
      "text": "Which method turns a List into a Stream?",
      "selectedIndex": 1,
      "correctIndex": 1,
      "correct": true,
      "explanation": "Collection.stream() returns a sequential Stream over the elements."
    },
    {
      "questionId": 2,
      "text": "Which operation is a terminal operation?",
      "selectedIndex": 0,
      "correctIndex": 2,
      "correct": false,
      "explanation": "collect is terminal; map, filter and peek are intermediate operations."
    }
  ]
}
```

### List attempts of a quiz

```bash
curl http://localhost:8084/api/quizzes/1/attempts
```

Response `200 OK`:

```json
[
  {
    "id": 1,
    "answers": [1, 0],
    "score": 1,
    "totalQuestions": 2,
    "submittedAt": "2026-11-16T10:20:05.456Z"
  }
]
```

## Validation rules

| Field | Rule |
|---|---|
| `topic` | Required, not blank, max 200 characters, no line breaks/control characters and no `<` or `>` |
| `difficulty` | Required, one of `EASY`, `MEDIUM`, `HARD` |
| `numberOfQuestions` | Required, between 1 and 10 |
| `answers` | Not empty, at most 10; each answer not null and between 0 and 3 |
| `answers` (count) | Must equal the number of questions in the quiz (checked in the service) |

## Error format

All errors go through `GlobalExceptionHandler` and return an `ErrorResponse`.
`fieldErrors` is filled only for validation errors. Otherwise it is an empty list.

```json
{
  "timestamp": "2026-11-16T10:25:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/quizzes/generate",
  "fieldErrors": [
    { "field": "numberOfQuestions", "message": "numberOfQuestions must be at most 10" }
  ]
}
```

| Status | When |
|---|---|
| 400 Bad Request | Validation failed (`@Valid`); body missing or not valid JSON (for example an unknown difficulty); id too large for a number; wrong number of answers |
| 404 Not Found | Quiz id does not exist; no endpoint at this path (ids must be numbers, so `/api/quizzes/abc` is a 404) |
| 405 Method Not Allowed | HTTP method not supported on this path (for example `GET /api/quizzes/generate`) |
| 415 Unsupported Media Type | Request body is not `application/json` |
| 500 Internal Server Error | Any unexpected error (details are logged, not sent to the client) |
| 502 Bad Gateway | The AI call failed or returned an invalid quiz (`AiGenerationException`) |

## How AI output is validated

`ClaudeQuizGenerator` asks Claude for structured output (`ChatClient ... .entity(GeneratedQuiz.class)`).
Network errors, API errors and unparseable JSON become an `AiGenerationException`.

Then `QuizService.validate` checks the result **before anything is saved**:

- At least one question is returned, and the count equals `numberOfQuestions`.
- No question is missing (null).
- Question text is not blank and at most 1000 characters.
- Exactly 4 options per question.
- Each option is not blank and at most 500 characters.
- All 4 options are different (trimmed, case-insensitive).
- `correctOptionIndex` is present (a missing value is rejected, never treated as 0) and between 0 and 3.
- The explanation, if present, is at most 2000 characters.

If any check fails, an `AiGenerationException` is thrown and the client gets HTTP 502.
These limits match the database column sizes, so saving never fails on length.

## Project structure

Base package: `com.soufiane.quizapi`

```
ai/           QuizGenerator interface, ClaudeQuizGenerator (Spring AI), AiConfig, GeneratedQuiz/GeneratedQuestion records
controller/   QuizController: REST endpoints only, no business logic
dto/          Request/response records (no JPA entities are ever returned)
exception/    Custom exceptions and GlobalExceptionHandler
model/        JPA entities: Quiz, Question, Attempt, Difficulty enum
repository/   Spring Data JPA repositories: QuizRepository, AttemptRepository
service/      QuizService: generation, validation of AI output, scoring, attempts
```

## Design choices

- **`QuizGenerator` is an interface.** The service does not depend on Claude or Spring AI directly.
  Tests mock the interface, so they never call the real (paid) AI. Another AI provider could be added
  with a new implementation.
- **Answers are hidden.** `QuestionResponse` has no `correctOptionIndex` and no explanation.
  The client only sees them in `QuestionCorrection`, after submitting. Otherwise the quiz would be pointless.
- **AI output is validated.** An AI model can return the wrong number of questions, duplicate options
  or an out-of-range index. Checking before saving keeps bad data out of the database and gives a clear 502.
- **The AI call is not inside a transaction.** Generation can take many seconds. It should not hold a
  database connection open.

## Running tests

```bash
./mvnw clean test
```

Tests use an H2 in-memory database (MySQL mode) and a mocked `QuizGenerator`.
They need **neither MySQL nor an Anthropic API key**. The test `application.properties`
does not import `.env` and uses a fake API key.

## How I used Claude Code

### CLAUDE.md

The `CLAUDE.md` file holds the project rules for Claude Code: tech stack, Lombok rules,
package structure, domain model, endpoint list, ten rules to always follow
(DTOs only, hidden answers, validation, JSON errors, AI behind an interface, structured output,
AI output checks, no real AI in tests, tests per endpoint, secrets from environment variables),
and the build/run commands.

### Subagents (`.claude/agents/`)

| Subagent | Role | Tools |
|---|---|---|
| `test-writer` | Writes and runs JUnit tests after a feature is added | Read, Grep, Glob, Write, Edit, Bash |
| `code-reviewer` | Read-only review of code quality and security | Read, Grep, Glob |
| `readme-writer` | Writes and updates this README from the real code | Read, Grep, Glob, Write, Edit |

### What the agents found / fixed:

- **Wrong answer key risk:** a missing `correctOptionIndex` in the AI output silently became 0. Now it is an `Integer` and a missing value is rejected.
- **Question order:** scoring compares answers by position, but `@OrderColumn` was on the unsupported `mappedBy` side (Hibernate warning HHH160246). `Quiz` now owns the relationship with an indexed list. A first attempt with `@OrderBy` was caught by the tests: it loaded each question 4 times.
- **Prompt injection / log forging:** the topic may not contain line breaks, control characters, `<` or `>`, and is wrapped in `<topic>` tags that the system prompt treats as data only.
- **Error handling:** wrong Content-Type now gives 415 instead of 500; non-numeric ids give 404 and `GET /generate` gives 405 instead of a confusing 400; `answers` is capped at 10.
- **Config:** a missing `ANTHROPIC_API_KEY` now fails at startup instead of on the first request; model switched to `claude-sonnet-5-5`.
- **Smaller:** attempts endpoint counts questions in SQL instead of loading them; rejected AI output is logged once; the 0-3 range is defined once (`Question.MAX_OPTION_INDEX`); `contextLoads` mocks the AI; a full-flow test (generate, get, submit, attempts) was added.

## Notes / limitations

- There is **no authentication and no rate limiting**. `POST /api/quizzes/generate` calls a paid AI API,
  so do not expose this app publicly as it is.
- `spring.jpa.hibernate.ddl-auto=update` and `spring.jpa.show-sql=true` are for development.
  Use a migration tool (for example Flyway) and turn off SQL logging for production.