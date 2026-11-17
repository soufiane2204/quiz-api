package com.soufiane.quizapi.repository;

import com.soufiane.quizapi.model.Difficulty;
import com.soufiane.quizapi.model.Quiz;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /**
     * Loads a quiz together with its questions and their options in one query.
     * Needed because open-in-view is disabled, so lazy collections can't be loaded later in the controller.
     */
    @EntityGraph(attributePaths = {"questions", "questions.options"})
    Optional<Quiz> findWithQuestionsById(Long id);

    /**
     * Lightweight list for GET /api/quizzes: counts the questions in SQL
     * instead of loading every question of every quiz.
     */
    @Query("""
            select q.id as id,
                   q.topic as topic,
                   q.difficulty as difficulty,
                   q.createdAt as createdAt,
                   size(q.questions) as questionCount
            from Quiz q
            order by q.createdAt desc
            """)
    List<QuizSummary> findAllSummaries();

    /**
     * Number of questions of one quiz, counted in SQL (no questions loaded).
     * Empty if the quiz does not exist.
     */
    @Query("select size(q.questions) from Quiz q where q.id = :id")
    Optional<Integer> countQuestionsById(Long id);

    /** Read-only projection used by {@link #findAllSummaries()}. */
    interface QuizSummary {
        Long getId();
        String getTopic();
        Difficulty getDifficulty();
        Instant getCreatedAt();
        int getQuestionCount();
    }
}