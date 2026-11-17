package com.soufiane.quizapi.ai;

import com.soufiane.quizapi.model.Difficulty;

/**
 * Hides the AI behind an interface (CLAUDE.md rule 5).
 * Tests mock this interface and never call the real AI (rule 8).
 */
public interface QuizGenerator {

    /**
     * Generates quiz questions on a topic.
     *
     * @throws com.soufiane.quizapi.exception.AiGenerationException if the AI call fails or returns nothing
     */
    GeneratedQuiz generate(String topic, Difficulty difficulty, int numberOfQuestions);
}