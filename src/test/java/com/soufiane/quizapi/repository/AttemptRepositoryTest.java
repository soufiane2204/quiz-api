package com.soufiane.quizapi.repository;

import com.soufiane.quizapi.model.Attempt;
import com.soufiane.quizapi.model.Difficulty;
import com.soufiane.quizapi.model.Quiz;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AttemptRepositoryTest {

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private TestEntityManager em;

    private Quiz persistQuiz(String topic) {
        return em.persist(Quiz.builder().topic(topic).difficulty(Difficulty.EASY).build());
    }

    private Attempt attempt(Quiz quiz, List<Integer> answers, int score, Instant submittedAt) {
        return Attempt.builder()
                .quiz(quiz)
                .answers(new ArrayList<>(answers))
                .score(score)
                .submittedAt(submittedAt)
                .build();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void findByQuizId_returnsOnlyThatQuizsAttempts_newestFirst_answersInOrder() {
        Quiz quiz = persistQuiz("Java streams");
        Quiz other = persistQuiz("Other");

        attemptRepository.save(attempt(quiz, List.of(0, 1, 2), 1, Instant.parse("2026-01-01T10:00:00Z")));
        attemptRepository.save(attempt(quiz, List.of(3, 2, 1), 3, Instant.parse("2026-02-01T10:00:00Z")));
        attemptRepository.save(attempt(other, List.of(1, 1, 1), 2, Instant.parse("2026-03-01T10:00:00Z")));
        flushAndClear();

        List<Attempt> attempts = attemptRepository.findByQuizIdOrderBySubmittedAtDesc(quiz.getId());
        em.clear(); // detach: answers must already be loaded by the entity graph

        assertThat(attempts).hasSize(2);
        assertThat(attempts).extracting(Attempt::getScore).containsExactly(3, 1);
        assertThat(attempts.get(0).getSubmittedAt()).isEqualTo(Instant.parse("2026-02-01T10:00:00Z"));
        assertThat(attempts.get(0).getAnswers()).containsExactly(3, 2, 1);
        assertThat(attempts.get(1).getAnswers()).containsExactly(0, 1, 2);
    }

    @Test
    void save_setsSubmittedAtAutomatically() {
        Quiz quiz = persistQuiz("Auto date");
        Instant before = Instant.now();

        Long id = attemptRepository.save(attempt(quiz, List.of(0), 1, null)).getId();
        flushAndClear();

        Attempt reloaded = attemptRepository.findById(id).orElseThrow();
        assertThat(reloaded.getSubmittedAt()).isNotNull();
        assertThat(reloaded.getSubmittedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(reloaded.getSubmittedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
    }

    @Test
    void findByQuizId_quizWithoutAttempts_returnsEmptyList() {
        Quiz quiz = persistQuiz("No attempts");
        flushAndClear();

        assertThat(attemptRepository.findByQuizIdOrderBySubmittedAtDesc(quiz.getId())).isEmpty();
    }

    @Test
    void findByQuizId_unknownQuiz_returnsEmptyList() {
        assertThat(attemptRepository.findByQuizIdOrderBySubmittedAtDesc(999_999L)).isEmpty();
    }
}