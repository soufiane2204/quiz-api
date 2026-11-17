package com.soufiane.quizapi.dto;

import java.util.List;

/** Response of POST /api/quizzes/{id}/submit: score plus a correction per question. */
public record SubmitResultResponse(
        Long attemptId,
        Long quizId,
        int score,
        int totalQuestions,
        List<QuestionCorrection> corrections
) {
}