package com.soufiane.quizapi.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private  Instant createdAt;

    /**
     * Quiz owns this relationship (no mappedBy), so @OrderColumn is supported and the list is indexed:
     * questions keep the order the AI returned. Scoring compares answers[i] with questions[i], so this matters.
     * (Hibernate warning HHH160246 forbids @OrderColumn on a mappedBy side; @OrderBy would make the list
     * an unordered "bag", which gets duplicated when fetched together with the options.)
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "quiz_id", nullable = false)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Adds a question and keeps both sides of the relationship in sync. */
    public void addQuestion(Question question) {
        questions.add(question);
        question.setQuiz(this);
    }
}