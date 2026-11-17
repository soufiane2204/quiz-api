package com.soufiane.quizapi.repository;

import com.soufiane.quizapi.model.Difficulty;
import com.soufiane.quizapi.model.Question;
import com.soufiane.quizapi.model.Quiz;
import com.soufiane.quizapi.repository.QuizRepository.QuizSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class QuizRepositoryTest {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private TestEntityManager em;

    // ---------- helpers ----------

    private static Question question(String text, List<String> options, int correct, String explanation) {
        return Question.builder()
                .text(text)
                .options(new java.util.ArrayList<>(options))
                .correctOptionIndex(correct)
                .explanation(explanation)
                .build();
    }

    private static Quiz quiz(String topic, Difficulty difficulty) {
        return Quiz.builder().topic(topic).difficulty(difficulty).build();
    }

    private Quiz quizWithTwoQuestions() {
        Quiz quiz = quiz("Java streams", Difficulty.MEDIUM);
        quiz.addQuestion(question("Which op is terminal?",
                List.of("map", "filter", "collect", "peek"), 2, "collect is terminal"));
        quiz.addQuestion(question("Are streams reusable?",
                List.of("Yes", "No", "Only parallel", "Only sequential"), 1, "A stream can be consumed once"));
        return quiz;
    }

    /** Pushes pending SQL and empties the persistence context so the next read hits the DB. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // ---------- findWithQuestionsById ----------

    @Test
    void findWithQuestionsById_returnsQuestionsAndOptionsInSavedOrder() {
        Long id = quizRepository.save(quizWithTwoQuestions()).getId();
        flushAndClear();

        Optional<Quiz> found = quizRepository.findWithQuestionsById(id);

        assertThat(found).isPresent();
        Quiz quiz = found.get();
        assertThat(quiz.getTopic()).isEqualTo("Java streams");
        assertThat(quiz.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(quiz.getQuestions()).hasSize(2);

        Question q1 = quiz.getQuestions().get(0);
        assertThat(q1.getText()).isEqualTo("Which op is terminal?");
        assertThat(q1.getOptions()).containsExactly("map", "filter", "collect", "peek");
        assertThat(q1.getCorrectOptionIndex()).isEqualTo(2);
        assertThat(q1.getExplanation()).isEqualTo("collect is terminal");
        assertThat(q1.getQuiz().getId()).isEqualTo(id);

        Question q2 = quiz.getQuestions().get(1);
        assertThat(q2.getText()).isEqualTo("Are streams reusable?");
        assertThat(q2.getOptions()).containsExactly("Yes", "No", "Only parallel", "Only sequential");
        assertThat(q2.getCorrectOptionIndex()).isEqualTo(1);
        assertThat(q2.getExplanation()).isEqualTo("A stream can be consumed once");
    }

    @Test
    void findWithQuestionsById_loadsCollectionsEagerly_usableAfterDetach() {
        Long id = quizRepository.save(quizWithTwoQuestions()).getId();
        flushAndClear();

        Quiz quiz = quizRepository.findWithQuestionsById(id).orElseThrow();
        em.clear(); // detach: lazy loading would now fail

        // With open-in-view disabled, the entity graph must have loaded everything already.
        assertThat(quiz.getQuestions()).hasSize(2);
        assertThat(quiz.getQuestions().get(1).getOptions()).hasSize(4);
    }

    @Test
    void findWithQuestionsById_unknownId_returnsEmpty() {
        assertThat(quizRepository.findWithQuestionsById(999_999L)).isEmpty();
    }

    // ---------- findAllSummaries ----------

    @Test
    void findAllSummaries_newestFirst_withCorrectQuestionCount() {
        Quiz oldest = quizWithTwoQuestions();
        oldest.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));

        Quiz empty = quiz("Empty quiz", Difficulty.EASY);
        empty.setCreatedAt(Instant.parse("2026-03-01T10:00:00Z"));

        Quiz newest = quiz("Spring AI", Difficulty.HARD);
        newest.setCreatedAt(Instant.parse("2026-06-01T10:00:00Z"));
        newest.addQuestion(question("Q?", List.of("a", "b", "c", "d"), 0, "because"));

        quizRepository.saveAll(List.of(oldest, empty, newest));
        flushAndClear();

        List<QuizSummary> summaries = quizRepository.findAllSummaries();

        assertThat(summaries).extracting(QuizSummary::getTopic)
                .containsExactly("Spring AI", "Empty quiz", "Java streams");
        assertThat(summaries).extracting(QuizSummary::getQuestionCount)
                .containsExactly(1, 0, 2);
        assertThat(summaries).extracting(QuizSummary::getDifficulty)
                .containsExactly(Difficulty.HARD, Difficulty.EASY, Difficulty.MEDIUM);
        assertThat(summaries.get(0).getId()).isEqualTo(newest.getId());
        assertThat(summaries.get(0).getCreatedAt()).isEqualTo(Instant.parse("2026-06-01T10:00:00Z"));
    }

    @Test
    void findAllSummaries_noQuizzes_returnsEmptyList() {
        assertThat(quizRepository.findAllSummaries()).isEmpty();
    }

    // ---------- lifecycle ----------

    @Test
    void save_setsCreatedAtAutomatically() {
        Instant before = Instant.now();
        Quiz saved = quizRepository.save(quiz("Auto date", Difficulty.EASY));
        flushAndClear();

        Quiz reloaded = quizRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        // allow small precision loss from DB timestamp storage
        assertThat(reloaded.getCreatedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(reloaded.getCreatedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
    }

    @Test
    void save_keepsExplicitCreatedAt() {
        Instant fixed = Instant.parse("2025-05-05T05:05:05Z");
        Quiz quiz = quiz("Fixed date", Difficulty.EASY);
        quiz.setCreatedAt(fixed);
        Long id = quizRepository.save(quiz).getId();
        flushAndClear();

        assertThat(quizRepository.findById(id).orElseThrow().getCreatedAt()).isEqualTo(fixed);
    }

    // ---------- cascade / orphanRemoval ----------

    @Test
    void delete_removesItsQuestions() {
        Quiz saved = quizRepository.save(quizWithTwoQuestions());
        List<Long> questionIds = saved.getQuestions().stream().map(Question::getId).toList();
        flushAndClear();

        quizRepository.delete(quizRepository.findById(saved.getId()).orElseThrow());
        flushAndClear();

        assertThat(quizRepository.findById(saved.getId())).isEmpty();
        questionIds.forEach(qid -> assertThat(em.find(Question.class, qid)).isNull());
    }

    @Test
    void removingQuestionFromList_deletesIt_orphanRemoval() {
        Quiz saved = quizRepository.save(quizWithTwoQuestions());
        Long removedId = saved.getQuestions().get(0).getId();
        Long keptId = saved.getQuestions().get(1).getId();
        flushAndClear();

        Quiz quiz = quizRepository.findWithQuestionsById(saved.getId()).orElseThrow();
        quiz.getQuestions().remove(0);
        flushAndClear();

        assertThat(em.find(Question.class, removedId)).isNull();
        Quiz reloaded = quizRepository.findWithQuestionsById(saved.getId()).orElseThrow();
        assertThat(reloaded.getQuestions()).extracting(Question::getId).containsExactly(keptId);
        assertThat(reloaded.getQuestions().get(0).getText()).isEqualTo("Are streams reusable?");
    }
}