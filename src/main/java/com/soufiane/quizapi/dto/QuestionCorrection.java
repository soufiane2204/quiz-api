package com.soufiane.quizapi.dto;

/** Correction of one question, returned only AFTER the quiz is submitted. */
public record QuestionCorrection(
        Long questionId,
        String text,
        int selectedIndex,
        int correctIndex,
        boolean correct,
        String explanation
) {
}