package com.soufiane.quizapi.dto;

import com.soufiane.quizapi.model.Question;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of POST /api/quizzes/{id}/submit.
 * One selected option index (0-3) per question, in question order.
 * The service checks that the number of answers matches the number of questions.
 */
public record SubmitAnswersRequest(

        @NotEmpty(message = "answers must contain at least one answer")
        @Size(max = 10, message = "answers can contain at most {max} answers")
        List<
                @NotNull(message = "an answer cannot be null")
                @Min(value = 0, message = "an answer must be between 0 and " + Question.MAX_OPTION_INDEX)
                @Max(value = Question.MAX_OPTION_INDEX, message = "an answer must be between 0 and " + Question.MAX_OPTION_INDEX)
                Integer> answers
) {
}
