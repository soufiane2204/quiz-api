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
import com.soufiane.quizapi.model.Question;
import com.soufiane.quizapi.model.Quiz;
import com.soufiane.quizapi.repository.AttemptRepository;
import com.soufiane.quizapi.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    /** Same limits as the columns in the Question entity, so saving never fails on length. */
    static final int MAX_QUESTION_TEXT = 1000;
    static final int MAX_OPTION_TEXT = 500;
    static final int MAX_EXPLANATION = 2000;

    private final QuizGenerator quizGenerator;
    private final QuizRepository quizRepository;
    private final AttemptRepository attemptRepository;

    /**
     * Asks the AI for a quiz, checks the result, saves it and returns it WITHOUT the correct answers.
     * Deliberately not @Transactional: the AI call can take many seconds and must not hold a
     * database connection. quizRepository.save(...) runs in its own transaction.
     */
    public QuizResponse generateQuiz(GenerateQuizRequest request) {
        String topic = request.topic().trim();
        int requested = request.numberOfQuestions();

        GeneratedQuiz generated = quizGenerator.generate(topic, request.difficulty(), requested);
        validate(generated, requested);

        Quiz quiz = Quiz.builder()
                .topic(topic)
                .difficulty(request.difficulty())
                .build();
        for (GeneratedQuestion g : generated.questions()) {
            quiz.addQuestion(Question.builder()
                    .text(g.text().trim())
                    .options(new ArrayList<>(g.options().stream().map(String::trim).toList()))
                    .correctOptionIndex(g.correctOptionIndex())
                    .explanation(g.explanation() == null ? null : g.explanation().trim())
                    .build());
        }

        Quiz saved = quizRepository.save(quiz);
        log.info("Generated quiz {} on '{}' ({}, {} questions)", saved.getId(), topic, request.difficulty(), requested);
        return QuizResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<QuizSummaryResponse> listQuizzes() {
        return quizRepository.findAllSummaries().stream()
                .map(QuizSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuiz(Long quizId) {
        return QuizResponse.from(findQuizWithQuestions(quizId));
    }

    /** Scores the answers, saves an Attempt and returns the score with a correction per question. */
    @Transactional
    public SubmitResultResponse submit(Long quizId, SubmitAnswersRequest request) {
        Quiz quiz = findQuizWithQuestions(quizId);
        List<Question> questions = quiz.getQuestions();
        List<Integer> answers = request.answers();

        if (answers.size() != questions.size()) {
            throw new InvalidSubmissionException(
                    "Expected " + questions.size() + " answers but got " + answers.size());
        }

        List<QuestionCorrection> corrections = new ArrayList<>(questions.size());
        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            int selected = answers.get(i);
            boolean correct = selected == question.getCorrectOptionIndex();
            if (correct) {
                score++;
            }
            corrections.add(new QuestionCorrection(
                    question.getId(),
                    question.getText(),
                    selected,
                    question.getCorrectOptionIndex(),
                    correct,
                    question.getExplanation()));
        }

        Attempt attempt = attemptRepository.save(Attempt.builder()
                .quiz(quiz)
                .answers(new ArrayList<>(answers))
                .score(score)
                .build());

        return new SubmitResultResponse(attempt.getId(), quiz.getId(), score, questions.size(), corrections);
    }

    @Transactional(readOnly = true)
    public List<AttemptResponse> getAttempts(Long quizId) {
        int totalQuestions = quizRepository.countQuestionsById(quizId)
                .orElseThrow(() -> new QuizNotFoundException(quizId));
        return attemptRepository.findByQuizIdOrderBySubmittedAtDesc(quizId).stream()
                .map(attempt -> AttemptResponse.from(attempt, totalQuestions))
                .toList();
    }

    // ---- helpers ----

    private Quiz findQuizWithQuestions(Long quizId) {
        return quizRepository.findWithQuestionsById(quizId)
                .orElseThrow(() -> new QuizNotFoundException(quizId));
    }

    /**
     * Checks the AI output before anything is saved (CLAUDE.md rule 7).
     * Any problem throws AiGenerationException, which becomes HTTP 502.
     */
    void validate(GeneratedQuiz generated, int requested) {
        if (generated == null || generated.questions() == null || generated.questions().isEmpty()) {
            throw invalid("no questions were returned");
        }
        List<GeneratedQuestion> questions = generated.questions();
        if (questions.size() != requested) {
            throw invalid("expected " + requested + " questions but got " + questions.size());
        }

        for (int i = 0; i < questions.size(); i++) {
            GeneratedQuestion q = questions.get(i);
            String where = "question " + (i + 1) + ": ";
            if (q == null) {
                throw invalid(where + "is missing");
            }
            if (isBlank(q.text()) || q.text().trim().length() > MAX_QUESTION_TEXT) {
                throw invalid(where + "text is blank or too long");
            }
            if (q.options() == null || q.options().size() != Question.OPTIONS_PER_QUESTION) {
                throw invalid(where + "must have exactly " + Question.OPTIONS_PER_QUESTION + " options");
            }
            Set<String> distinct = new HashSet<>();
            for (String option : q.options()) {
                if (isBlank(option) || option.trim().length() > MAX_OPTION_TEXT) {
                    throw invalid(where + "an option is blank or too long");
                }
                if (!distinct.add(option.trim().toLowerCase(Locale.ROOT))) {
                    throw invalid(where + "options are not all different");
                }
            }
            if (q.correctOptionIndex() == null) {
                throw invalid(where + "correctOptionIndex is missing");
            }
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() > Question.MAX_OPTION_INDEX) {
                throw invalid(where + "correctOptionIndex " + q.correctOptionIndex()
                        + " is not between 0 and " + Question.MAX_OPTION_INDEX);
            }
            if (q.explanation() != null && q.explanation().trim().length() > MAX_EXPLANATION) {
                throw invalid(where + "explanation is too long");
            }
        }
    }

    /** Not logged here: GlobalExceptionHandler logs every AiGenerationException once. */
    private static AiGenerationException invalid(String reason) {
        return new AiGenerationException("The AI returned an invalid quiz: " + reason);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
