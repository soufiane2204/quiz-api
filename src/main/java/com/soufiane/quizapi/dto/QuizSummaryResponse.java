package com.soufiane.quizapi.dto;

import com.soufiane.quizapi.model.Difficulty;
import com.soufiane.quizapi.repository.QuizRepository;

import java.time.Instant;

/** One row of GET /api/quizzes. */
public record  QuizSummaryResponse(
        Long id,
        String topic,
        Difficulty difficulty,
        Instant createdAt,
        int questionCount
) {

    public static QuizSummaryResponse from(QuizRepository.QuizSummary summary) {
        return new QuizSummaryResponse(

                summary.getId(),
                summary.getTopic(),
                summary.getDifficulty(),
                summary.getCreatedAt(),
                summary.getQuestionCount()
        );
    }
}