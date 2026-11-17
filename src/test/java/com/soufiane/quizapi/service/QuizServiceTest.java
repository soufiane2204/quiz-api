package com.soufiane.quizapi.service;

import com.soufiane.quizapi.ai.GeneratedQuestion;
import com.soufiane.quizapi.ai.GeneratedQuiz;
import com.soufiane.quizapi.ai.QuizGenerator;
import com.soufiane.quizapi.dto.AttemptResponse;
import com.soufiane.quizapi.dto.GenerateQuizRequest;
import com.soufiane.quizapi.dto.QuestionCorrection;
import com.soufiane.quizapi.dto.QuizResponse;
import com.soufiane.quizapi.dto.QuizSummaryResponse;
import com.soufiane.quizapi.dto.SubmitAnswersRequest;
import com.soufiane.quizapi.dto.SubmitResultResponse;
import com.soufiane.quizapi.exception.AiGenerationException;
import com.soufiane.quizapi.exception.InvalidSubmissionException;
import com.soufiane.quizapi.exception.QuizNotFoundException;
import com.soufiane.quizapi.model.Attempt;
import com.soufiane.quizapi.model.Difficulty;
import com.soufiane.quizapi.model.Question;
import com.soufiane.quizapi.model.Quiz;
import com.soufiane.quizapi.repository.AttemptRepository;
import com.soufiane.quizapi.repository.QuizRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pure unit tests for QuizService: no Spring context, AI and repositories are Mockito mocks. */
@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private QuizGenerator quizGenerator;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @InjectMocks
    private QuizService quizService;

    // ---- helpers ----

    private static GeneratedQuestion validQuestion(int n) {
        return new GeneratedQuestion("Question " + n + "?",
                List.of("Option A" + n, "Option B" + n, "Option C" + n, "Option D" + n),
                n % 4, "Because " + n);
    }

    private static GeneratedQuiz quizOf(GeneratedQuestion... questions) {
        return new GeneratedQuiz(Arrays.asList(questions));
    }

    /** A persisted-looking quiz with ids and the given correct indexes. */
    private static Quiz storedQuiz(long id, int... correctIndexes) {
        Quiz quiz = Quiz.builder().id(id).topic("Java").difficulty(Difficulty.EASY).createdAt(CREATED).build();
        for (int i = 0; i < correctIndexes.length; i++) {
            quiz.addQuestion(Question.builder()
                    .id(100L + i)
                    .text("Q" + i)
                    .options(new ArrayList<>(List.of("a", "b", "c", "d")))
                    .correctOptionIndex(correctIndexes[i])
                    .explanation("E" + i)
                    .build());
        }
        return quiz;
    }

    // ------------------------------------------------------------------ generateQuiz

    @Nested
    class GenerateQuiz {

        @Test
        void happyPathSavesQuizAndReturnsResponseWithoutAnswers() {
            GeneratedQuestion q1 = new GeneratedQuestion("  What is a stream?  ",
                    List.of(" A pipeline ", "A list", "A thread", "A file"), 0, "  Streams are pipelines.  ");
            GeneratedQuestion q2 = new GeneratedQuestion("What does map do?",
                    List.of("Filters", "Transforms", "Sorts", "Counts"), 1, null);
            when(quizGenerator.generate("Java streams", Difficulty.HARD, 2)).thenReturn(quizOf(q1, q2));
            when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> {
                Quiz q = inv.getArgument(0);
                q.setId(42L);
                q.setCreatedAt(CREATED);
                return q;
            });

            QuizResponse response = quizService.generateQuiz(
                    new GenerateQuizRequest("  Java streams  ", Difficulty.HARD, 2));

            ArgumentCaptor<Quiz> captor = ArgumentCaptor.forClass(Quiz.class);
            verify(quizRepository).save(captor.capture());
            Quiz saved = captor.getValue();
            assertThat(saved.getTopic()).isEqualTo("Java streams");
            assertThat(saved.getDifficulty()).isEqualTo(Difficulty.HARD);
            assertThat(saved.getQuestions()).hasSize(2);

            Question s1 = saved.getQuestions().get(0);
            assertThat(s1.getText()).isEqualTo("What is a stream?");
            assertThat(s1.getOptions()).containsExactly("A pipeline", "A list", "A thread", "A file");
            assertThat(s1.getCorrectOptionIndex()).isZero();
            assertThat(s1.getExplanation()).isEqualTo("Streams are pipelines.");
            assertThat(s1.getQuiz()).isSameAs(saved);

            Question s2 = saved.getQuestions().get(1);
            assertThat(s2.getOptions()).containsExactly("Filters", "Transforms", "Sorts", "Counts");
            assertThat(s2.getCorrectOptionIndex()).isEqualTo(1);
            assertThat(s2.getExplanation()).isNull();

            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.topic()).isEqualTo("Java streams");
            assertThat(response.difficulty()).isEqualTo(Difficulty.HARD);
            assertThat(response.createdAt()).isEqualTo(CREATED);
            assertThat(response.questions()).hasSize(2);
            assertThat(response.questions().get(0).text()).isEqualTo("What is a stream?");
            assertThat(response.questions().get(1).options()).containsExactly("Filters", "Transforms", "Sorts", "Counts");
        }

        @Test
        void generatorFailurePropagatesAndNothingIsSaved() {
            when(quizGenerator.generate(anyString(), any(), anyInt()))
                    .thenThrow(new AiGenerationException("AI down"));

            assertThatThrownBy(() -> quizService.generateQuiz(new GenerateQuizRequest("Java", Difficulty.EASY, 1)))
                    .isInstanceOf(AiGenerationException.class)
                    .hasMessage("AI down");
            verifyNoInteractions(quizRepository, attemptRepository);
        }

        static Stream<Arguments> invalidAiOutputs() {
            List<String> four = List.of("A", "B", "C", "D");
            return Stream.of(
                    Arguments.of("null quiz", null, 1),
                    Arguments.of("null questions", new GeneratedQuiz(null), 1),
                    Arguments.of("empty questions", new GeneratedQuiz(List.of()), 1),
                    Arguments.of("too few questions", quizOf(validQuestion(1)), 2),
                    Arguments.of("too many questions", quizOf(validQuestion(1), validQuestion(2), validQuestion(3)), 2),
                    Arguments.of("null question", quizOf(validQuestion(1), null), 2),
                    Arguments.of("null text", quizOf(new GeneratedQuestion(null, four, 0, "e")), 1),
                    Arguments.of("blank text", quizOf(new GeneratedQuestion("   ", four, 0, "e")), 1),
                    Arguments.of("too long text", quizOf(new GeneratedQuestion("x".repeat(1001), four, 0, "e")), 1),
                    Arguments.of("null options", quizOf(new GeneratedQuestion("Q?", null, 0, "e")), 1),
                    Arguments.of("3 options", quizOf(new GeneratedQuestion("Q?", List.of("A", "B", "C"), 0, "e")), 1),
                    Arguments.of("5 options", quizOf(new GeneratedQuestion("Q?", List.of("A", "B", "C", "D", "E"), 0, "e")), 1),
                    Arguments.of("blank option", quizOf(new GeneratedQuestion("Q?", List.of("A", " ", "C", "D"), 0, "e")), 1),
                    Arguments.of("null option", quizOf(new GeneratedQuestion("Q?", Arrays.asList("A", null, "C", "D"), 0, "e")), 1),
                    Arguments.of("too long option", quizOf(new GeneratedQuestion("Q?", List.of("A", "x".repeat(501), "C", "D"), 0, "e")), 1),
                    Arguments.of("duplicate options (case/space insensitive)",
                            quizOf(new GeneratedQuestion("Q?", List.of("Java", "Kotlin", " JAVA ", "Scala"), 0, "e")), 1),
                    Arguments.of("missing index (null, must not become 0)", quizOf(new GeneratedQuestion("Q?", four, null, "e")), 1),
                    Arguments.of("index -1", quizOf(new GeneratedQuestion("Q?", four, -1, "e")), 1),
                    Arguments.of("index 4", quizOf(new GeneratedQuestion("Q?", four, 4, "e")), 1),
                    Arguments.of("too long explanation", quizOf(new GeneratedQuestion("Q?", four, 0, "x".repeat(2001))), 1),
                    Arguments.of("second question invalid", quizOf(validQuestion(1), new GeneratedQuestion("Q?", four, 9, "e")), 2)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidAiOutputs")
        void invalidAiOutputThrowsAndNothingIsSaved(String label, GeneratedQuiz aiOutput, int requested) {
            when(quizGenerator.generate("Java", Difficulty.MEDIUM, requested)).thenReturn(aiOutput);

            assertThatThrownBy(() -> quizService.generateQuiz(
                    new GenerateQuizRequest("Java", Difficulty.MEDIUM, requested)))
                    .isInstanceOf(AiGenerationException.class)
                    .hasMessageStartingWith("The AI returned an invalid quiz");
            verify(quizRepository, never()).save(any());
        }

        @Test
        void validateAcceptsBoundaryLengthsAndNullExplanation() {
            GeneratedQuiz ok = quizOf(new GeneratedQuestion("x".repeat(1000),
                    List.of("y".repeat(500), "B", "C", "D"), 3, null));
            quizService.validate(ok, 1); // must not throw

            GeneratedQuiz okExplanation = quizOf(new GeneratedQuestion("Q?",
                    List.of("A", "B", "C", "D"), 0, "z".repeat(2000)));
            quizService.validate(okExplanation, 1);
        }
    }

    // ------------------------------------------------------------------ listQuizzes / getQuiz

    @Test
    void listQuizzesMapsSummaries() {
        QuizRepository.QuizSummary summary = mock(QuizRepository.QuizSummary.class);
        when(summary.getId()).thenReturn(5L);
        when(summary.getTopic()).thenReturn("Spring");
        when(summary.getDifficulty()).thenReturn(Difficulty.MEDIUM);
        when(summary.getCreatedAt()).thenReturn(CREATED);
        when(summary.getQuestionCount()).thenReturn(4);
        when(quizRepository.findAllSummaries()).thenReturn(List.of(summary));

        List<QuizSummaryResponse> result = quizService.listQuizzes();

        assertThat(result).containsExactly(
                new QuizSummaryResponse(5L, "Spring", Difficulty.MEDIUM, CREATED, 4));
    }

    @Test
    void listQuizzesEmpty() {
        when(quizRepository.findAllSummaries()).thenReturn(List.of());
        assertThat(quizService.listQuizzes()).isEmpty();
    }

    @Test
    void getQuizReturnsResponse() {
        when(quizRepository.findWithQuestionsById(1L)).thenReturn(Optional.of(storedQuiz(1L, 0, 3)));

        QuizResponse response = quizService.getQuiz(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.questions()).hasSize(2);
        assertThat(response.questions().get(1).id()).isEqualTo(101L);
        assertThat(response.questions().get(1).options()).containsExactly("a", "b", "c", "d");
    }

    @Test
    void getQuizUnknownIdThrowsNotFound() {
        when(quizRepository.findWithQuestionsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getQuiz(99L))
                .isInstanceOf(QuizNotFoundException.class)
                .hasMessage("Quiz with id 99 not found");
    }

    // ------------------------------------------------------------------ submit

    @Nested
    class Submit {

        @Test
        void scoresAnswersSavesAttemptAndReturnsCorrections() {
            Quiz quiz = storedQuiz(1L, 0, 1, 2, 3);
            when(quizRepository.findWithQuestionsById(1L)).thenReturn(Optional.of(quiz));
            when(attemptRepository.save(any(Attempt.class))).thenAnswer(inv -> {
                Attempt a = inv.getArgument(0);
                a.setId(500L);
                return a;
            });

            // right, wrong, right, wrong
            SubmitResultResponse result = quizService.submit(1L, new SubmitAnswersRequest(List.of(0, 2, 2, 0)));

            assertThat(result.attemptId()).isEqualTo(500L);
            assertThat(result.quizId()).isEqualTo(1L);
            assertThat(result.score()).isEqualTo(2);
            assertThat(result.totalQuestions()).isEqualTo(4);
            assertThat(result.corrections()).containsExactly(
                    new QuestionCorrection(100L, "Q0", 0, 0, true, "E0"),
                    new QuestionCorrection(101L, "Q1", 2, 1, false, "E1"),
                    new QuestionCorrection(102L, "Q2", 2, 2, true, "E2"),
                    new QuestionCorrection(103L, "Q3", 0, 3, false, "E3"));

            ArgumentCaptor<Attempt> captor = ArgumentCaptor.forClass(Attempt.class);
            verify(attemptRepository).save(captor.capture());
            Attempt saved = captor.getValue();
            assertThat(saved.getQuiz()).isSameAs(quiz);
            assertThat(saved.getAnswers()).containsExactly(0, 2, 2, 0);
            assertThat(saved.getScore()).isEqualTo(2);
        }

        @Test
        void allCorrectAndAllWrong() {
            when(quizRepository.findWithQuestionsById(1L)).thenAnswer(inv -> Optional.of(storedQuiz(1L, 1, 1)));
            when(attemptRepository.save(any(Attempt.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(quizService.submit(1L, new SubmitAnswersRequest(List.of(1, 1))).score()).isEqualTo(2);
            assertThat(quizService.submit(1L, new SubmitAnswersRequest(List.of(0, 3))).score()).isZero();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 3})
        void answerCountMismatchThrowsAndNothingIsSaved(int count) {
            when(quizRepository.findWithQuestionsById(1L)).thenReturn(Optional.of(storedQuiz(1L, 0, 1)));
            List<Integer> answers = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                answers.add(0);
            }

            assertThatThrownBy(() -> quizService.submit(1L, new SubmitAnswersRequest(answers)))
                    .isInstanceOf(InvalidSubmissionException.class)
                    .hasMessage("Expected 2 answers but got " + count);
            verify(attemptRepository, never()).save(any());
        }

        @Test
        void unknownQuizThrowsNotFound() {
            when(quizRepository.findWithQuestionsById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> quizService.submit(99L, new SubmitAnswersRequest(List.of(0))))
                    .isInstanceOf(QuizNotFoundException.class);
            verifyNoInteractions(attemptRepository);
        }
    }

    // ------------------------------------------------------------------ getAttempts

    @Test
    void getAttemptsMapsWithTotalQuestions() {
        Quiz quiz = storedQuiz(1L, 0, 1, 2);
        when(quizRepository.countQuestionsById(1L)).thenReturn(Optional.of(3));
        Attempt newer = Attempt.builder().id(2L).quiz(quiz).answers(new ArrayList<>(List.of(0, 1, 2)))
                .score(3).submittedAt(CREATED.plusSeconds(60)).build();
        Attempt older = Attempt.builder().id(1L).quiz(quiz).answers(new ArrayList<>(List.of(3, 3, 3)))
                .score(0).submittedAt(CREATED).build();
        when(attemptRepository.findByQuizIdOrderBySubmittedAtDesc(1L)).thenReturn(List.of(newer, older));

        List<AttemptResponse> result = quizService.getAttempts(1L);

        assertThat(result).containsExactly(
                new AttemptResponse(2L, List.of(0, 1, 2), 3, 3, CREATED.plusSeconds(60)),
                new AttemptResponse(1L, List.of(3, 3, 3), 0, 3, CREATED));
    }

    @Test
    void getAttemptsUnknownQuizThrowsNotFound() {
        when(quizRepository.countQuestionsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getAttempts(99L))
                .isInstanceOf(QuizNotFoundException.class)
                .hasMessage("Quiz with id 99 not found");
        verifyNoInteractions(attemptRepository);
    }
}
