package com.soufiane.quizapi;

import com.soufiane.quizapi.ai.QuizGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class QuizApiApplicationTests {

    /** Replaces the real Claude-backed generator: tests never call the AI (CLAUDE.md rule 8). */
    @MockitoBean
    private QuizGenerator quizGenerator;

    @Test
    void contextLoads() {
    }

}
