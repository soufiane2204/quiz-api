package com.soufiane.quizapi.ai;

import java.util.List;

/**
 * One question exactly as the AI returns it (structured output).
 * Not trusted: QuizService checks it before anything is saved.
 */
public record GeneratedQuestion(
        String text,
        List<String> options,
        /* Integer, not int: if the AI leaves it out it stays null and is rejected, instead of silently becoming 0. */
        Integer correctOptionIndex,
        String explanation
) {
}