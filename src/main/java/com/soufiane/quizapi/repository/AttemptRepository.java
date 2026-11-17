package com.soufiane.quizapi.repository;

import com.soufiane.quizapi.model.Attempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /** All attempts for one quiz, newest first, with their answers loaded in the same query. */
    @EntityGraph(attributePaths = "answers")
    List<Attempt> findByQuizIdOrderBySubmittedAtDesc(Long quizId);
}