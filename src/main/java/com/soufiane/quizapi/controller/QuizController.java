package com.soufiane.quizapi.controller;

import com.soufiane.quizapi.dto.AttemptResponse;
import com.soufiane.quizapi.dto.GenerateQuizRequest;
import com.soufiane.quizapi.dto.QuizResponse;
import com.soufiane.quizapi.dto.QuizSummaryResponse;
import com.soufiane.quizapi.dto.SubmitAnswersRequest;
import com.soufiane.quizapi.dto.SubmitResultResponse;
import com.soufiane.quizapi.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for quizzes. No business logic here: everything is delegated to QuizService,
 * and only DTO records are returned (CLAUDE.md rule 1). Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /** AI generates a new quiz; returned WITHOUT the correct answers. */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizResponse generate(@Valid @RequestBody GenerateQuizRequest request) {
        return quizService.generateQuiz(request);
    }

    @GetMapping
    public List<QuizSummaryResponse> listQuizzes() {
        return quizService.listQuizzes();
    }

    /** One quiz WITHOUT the correct answers. */
    @GetMapping("/{id:\\d+}")
    public QuizResponse getQuiz(@PathVariable Long id) {
        return quizService.getQuiz(id);
    }

    /** Scores the answers and returns the score with a correction per question. */
    @PostMapping("/{id:\\d+}/submit")
    public SubmitResultResponse submit(@PathVariable Long id,
                                       @Valid @RequestBody SubmitAnswersRequest request) {
        return quizService.submit(id, request);
    }

    @GetMapping("/{id:\\d+}/attempts")
    public List<AttemptResponse> getAttempts(@PathVariable Long id) {
        return quizService.getAttempts(id);
    }
}
