# Quiz API — Project rules for Claude Code

## What this project is
A small Spring Boot REST API that uses AI to generate quiz questions on a topic
(for example "Java streams"), stores them, and scores the user's answers.
This is a practice project to learn Claude Code, subagents and Spring AI.
## Tech stack
- Java 17, Spring Boot 4.1, Spring AI 2.0, Maven
- Spring Web, Spring Data JPA, Validation
- MySQL 8 database (database name: `quiz_db`)
- Spring AI with Anthropic Claude
- Lombok for entities and services; Java records for DTOs
- JUnit 5, Mockito, MockMvc for tests
## Lombok rules
- Entities: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` if useful.
- NEVER use `@Data` or `@EqualsAndHashCode` / `@ToString` on JPA entities.
- Services and controllers: `@RequiredArgsConstructor` with `private final` fields.
- DTOs are Java records, so they don't need Lombok.
## Package structure
Base package: `com.soufiane.quizapi`

controller/   REST controllers only (no business logic)
service/      business logic
repository/   Spring Data JPA interfaces
model/        JPA entities
dto/          request/response records
ai/           everything related to the AI
exception/    custom exceptions + GlobalExceptionHandler
## Domain model
- `Quiz`: id, topic, difficulty (EASY / MEDIUM / HARD), createdAt, list of questions
- `Question`: id, text, options (exactly 4 strings), correctOptionIndex (0-3), explanation
- `Attempt`: id, quiz, answers (list of selected indexes), score, submittedAt
## Endpoints
| Method | Path | Description |
|---|---|---|
| POST | `/api/quizzes/generate` | Body: `{ topic, difficulty, numberOfQuestions (1-10) }`. AI generates the quiz, returned WITHOUT correct answers |
| GET | `/api/quizzes` | List all quizzes |
| GET | `/api/quizzes/{id}` | Get one quiz WITHOUT correct answers |
| POST | `/api/quizzes/{id}/submit` | Body: `{ answers: [0, 2, 1, ...] }`. Returns score + correction |
| GET | `/api/quizzes/{id}/attempts` | List previous attempts and scores |

## Rules (always follow)
1. Never return JPA entities from controllers — always map to DTO records.
2. Never send `correctOptionIndex` to the client before the quiz is submitted.
3. Validate all input with Jakarta Validation (`@NotBlank`, `@Min`, `@Max`, `@Valid`).
4. Errors go through `GlobalExceptionHandler` and return JSON.
5. The AI is hidden behind an interface `QuizGenerator` in the `ai` package.
6. The AI must return structured output (`ChatClient ... .entity(...)`).
7. Check the AI output before saving. If invalid, throw `AiGenerationException` (HTTP 502).
8. Tests NEVER call the real AI. Mock `QuizGenerator` in tests.
9. Every endpoint has at least one success test and one error test.
10. Secrets come from environment variables, never hard-coded.
## Commands
- Build and test: `./mvnw clean test`
- Run: `./mvnw spring-boot:run`
- The app runs on port 8084
- MySQL must be running locally on port 3306
## Subagents in this project
- `test-writer` — writes and runs tests after a feature is added
- `code-reviewer` — read-only review of quality and security
- `readme-writer` — writes and updates README.md