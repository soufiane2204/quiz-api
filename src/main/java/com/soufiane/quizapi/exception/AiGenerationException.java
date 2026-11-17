package com.soufiane.quizapi.exception;

/**
 * Thrown when the AI fails or returns an invalid quiz.
 * GlobalExceptionHandler maps it to HTTP 502 Bad Gateway (CLAUDE.md rule 7).
 */
public class AiGenerationException extends RuntimeException {

    public AiGenerationException(String message) {
        super(message);
    }

    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}