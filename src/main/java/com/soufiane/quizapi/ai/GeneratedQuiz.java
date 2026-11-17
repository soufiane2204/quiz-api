package com.soufiane.quizapi.ai;

import java.util.List;

/** The whole quiz as the AI returns it (structured output target of ChatClient.entity(...)). */
public record GeneratedQuiz(
        List<GeneratedQuestion> questions
) {
}