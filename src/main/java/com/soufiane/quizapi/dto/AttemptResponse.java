package com.soufiane.quizapi.dto;

import com.soufiane.quizapi.model.Attempt;

import java.time.Instant;
import java.util.List;

/** One previous attempt, returned by GET /api/quizzes/{id}/attempts. */
public record AttemptResponse(
        Long id,
        List<Integer> answers,
        int score,
        int totalQuestions,
        Instant submittedAt
) {

    /**
     * @param attempt        attempt loaded with its answers
     * @param totalQuestions number of questions in the quiz (passed in to avoid loading the quiz's questions)
     */
    public static AttemptResponse from(Attempt attempt, int totalQuestions) {
        return new AttemptResponse(
                attempt.getId(),
                List.copyOf(attempt.getAnswers()),
                attempt.getScore(),
                totalQuestions,
                attempt.getSubmittedAt()
        );
    }
}