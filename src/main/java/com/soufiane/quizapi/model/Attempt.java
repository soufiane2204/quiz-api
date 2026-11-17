package com.soufiane.quizapi.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /** The selected option index for each question, in question order. */
    @ElementCollection
    @CollectionTable(name = "attempt_answers", joinColumns = @JoinColumn(name = "attempt_id"))
    @OrderColumn(name = "question_position")
    @Column(name = "selected_index", nullable = false)
    @Builder.Default
    private List<Integer> answers = new ArrayList<>();

    /** Number of correct answers. */
    @Column(nullable = false)
    private int score;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @PrePersist
    void onCreate() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }
}