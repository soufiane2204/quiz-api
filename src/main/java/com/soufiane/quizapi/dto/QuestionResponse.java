package com.soufiane.quizapi.dto;

import com.soufiane.quizapi.model.Question;

import java.util.List;

/**
 * A question as shown to the user BEFORE submitting.
 * Deliberately has no correctOptionIndex and no explanation (CLAUDE.md rule 2).
 */
public record QuestionResponse(
        Long id,
        String text,
        List<String> options
) {

    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getText(),
                List.copyOf(question.getOptions())
        );
    }
}