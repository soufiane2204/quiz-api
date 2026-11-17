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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    /** Every question has exactly this many options. Single source of truth for the 0-3 index range. */
    public static final int OPTIONS_PER_QUESTION = 4;
    /** Highest valid option index (OPTIONS_PER_QUESTION - 1). A constant so it can be used in annotations. */
    public static final int MAX_OPTION_INDEX = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_text", nullable = false, length = 1000)
    private String text;

    /** Exactly 4 options. @OrderColumn keeps them in the same order as they were saved. */
    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "option_text", nullable = false, length = 500)
    @Builder.Default
    private List<String> options = new ArrayList<>();

    /** Index (0-3) of the correct option. Never sent to the client before submit. */
    @Column(name = "correct_option_index", nullable = false)
    private int correctOptionIndex;

    @Column(length = 2000)
    private String explanation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    /** Read-only side: the quiz_id column is written by Quiz.questions (the owning side). */
    @JoinColumn(name = "quiz_id", nullable = false, insertable = false, updatable = false)
    private Quiz quiz;
}