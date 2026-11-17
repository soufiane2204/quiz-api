package com.soufiane.quizapi.exception;

/**
 * Thrown when a submission is well-formed but doesn't fit the quiz,
 * e.g. the number of answers differs from the number of questions. Mapped to HTTP 400.
 */
public class InvalidSubmissionException extends RuntimeException {

    public InvalidSubmissionException(String message) {
        super(message);
    }
}
