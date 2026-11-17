package com.soufiane.quizapi.exception;

/** Thrown when no quiz exists with the given id. Mapped to HTTP 404. */
public class QuizNotFoundException extends RuntimeException {

    public QuizNotFoundException(Long quizId) {
        super("Quiz with id " + quizId + " not found");
    }
}
