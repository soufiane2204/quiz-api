package com.soufiane.quizapi.dto;

import com.soufiane.quizapi.model.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/quizzes/generate. */
public record GenerateQuizRequest(

        @NotBlank(message = "topic is required")
        @Size(max = 200, message = "topic must be at most 200 characters")
        // No control characters (newlines, tabs...) and no < >: prevents faking extra prompt lines,
        // closing the <topic> tag in the AI prompt, or forging log lines.
        @Pattern(regexp = "[^\\p{Cntrl}<>]*", message = "topic must not contain line breaks, control characters, < or >")
        String topic,

        @NotNull(message = "difficulty is required (EASY, MEDIUM or HARD)")
        Difficulty difficulty,

        @NotNull(message = "numberOfQuestions is required")
        @Min(value = 1, message = "numberOfQuestions must be at least 1")
        @Max(value = 10, message = "numberOfQuestions must be at most 10")
        Integer numberOfQuestions
) {
}