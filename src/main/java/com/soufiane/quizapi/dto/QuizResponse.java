package com.soufiane.quizapi.dto;

import com.soufiane.quizapi.model.Difficulty;
import com.soufiane.quizapi.model.Quiz;

import java.time.Instant;
import java.util.List;

/**
 * A full quiz WITHOUT the correct answers.
 * Returned by POST /api/quizzes/generate and GET /api/quizzes/{id}.
 * The quiz must be loaded with its questions (e.g. QuizRepository.findWithQuestionsById).
 */
public record QuizResponse(
        Long id,
        String topic,
        Difficulty difficulty,
        Instant createdAt,
        List<QuestionResponse> questions
) {

    public static QuizResponse from(Quiz quiz) {
        return new QuizResponse(
                quiz.getId(),
                quiz.getTopic(),
                quiz.getDifficulty(),
                quiz.getCreatedAt(),
                quiz.getQuestions().stream().map(QuestionResponse::from).toList()
        );
    }
}