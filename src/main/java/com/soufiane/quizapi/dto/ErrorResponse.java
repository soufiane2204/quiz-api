package com.soufiane.quizapi.dto;

import java.time.Instant;
import java.util.List;

/**
 * JSON body of every error response (CLAUDE.md rule 4).
 * fieldErrors is only filled for validation errors; otherwise it is an empty list.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {

    /** One invalid input field, e.g. { "field": "numberOfQuestions", "message": "must be at most 10" }. */
    public record FieldViolation(String field, String message) {
    }
}
