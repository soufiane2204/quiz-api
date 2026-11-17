package com.soufiane.quizapi;

import com.jayway.jsonpath.JsonPath;
import com.soufiane.quizapi.ai.GeneratedQuestion;
import com.soufiane.quizapi.ai.GeneratedQuiz;
import com.soufiane.quizapi.ai.QuizGenerator;
import com.soufiane.quizapi.model.Difficulty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full flow through every layer (MockMvc -> controller -> service -> JPA on H2), with only the AI mocked.
 * Catches problems unit tests can't: question order after a real reload, scoring against stored answers,
 * and nothing leaking correctOptionIndex.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuizFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizGenerator quizGenerator;

    @Test
    void generateGetSubmitAndListAttempts() throws Exception {
        // Correct answers deliberately differ per question, so a wrong question order gives a wrong score.
        when(quizGenerator.generate("Java streams", Difficulty.MEDIUM, 3)).thenReturn(new GeneratedQuiz(List.of(
                new GeneratedQuestion("First?", List.of("a1", "b1", "c1", "d1"), 2, "because 1"),
                new GeneratedQuestion("Second?", List.of("a2", "b2", "c2", "d2"), 0, "because 2"),
                new GeneratedQuestion("Third?", List.of("a3", "b3", "c3", "d3"), 3, "because 3"))));

        // 1. generate
        String generated = mockMvc.perform(post("/api/quizzes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"Java streams\",\"difficulty\":\"MEDIUM\",\"numberOfQuestions\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        assertThat(generated).doesNotContain("correctOptionIndex").doesNotContain("because");
        long quizId = ((Number) JsonPath.read(generated, "$.id")).longValue();

        // 2. get: reloaded from the database, same order, still no answers
        String fetched = mockMvc.perform(get("/api/quizzes/" + quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].text").value("First?"))
                .andExpect(jsonPath("$.questions[1].text").value("Second?"))
                .andExpect(jsonPath("$.questions[2].text").value("Third?"))
                .andExpect(jsonPath("$.questions[2].options[3]").value("d3"))
                .andReturn().getResponse().getContentAsString();
        assertThat(fetched).doesNotContain("correctOptionIndex").doesNotContain("because");

        // 3. submit: right, wrong, right
        mockMvc.perform(post("/api/quizzes/" + quizId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[2,1,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2))
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.corrections[0].correct").value(true))
                .andExpect(jsonPath("$.corrections[1].correct").value(false))
                .andExpect(jsonPath("$.corrections[1].correctIndex").value(0))
                .andExpect(jsonPath("$.corrections[1].explanation").value("because 2"))
                .andExpect(jsonPath("$.corrections[2].correct").value(true));

        // wrong number of answers is rejected and not stored
        mockMvc.perform(post("/api/quizzes/" + quizId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[2,1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Expected 3 answers but got 2"));

        // 4. attempts: exactly the one valid attempt
        mockMvc.perform(get("/api/quizzes/" + quizId + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].answers", hasSize(3)))
                .andExpect(jsonPath("$[0].score").value(2))
                .andExpect(jsonPath("$[0].totalQuestions").value(3));

        // 5. list contains the quiz with its question count
        mockMvc.perform(get("/api/quizzes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) quizId)))
                .andExpect(jsonPath("$[?(@.id == " + quizId + ")].questionCount", hasItem(3)));
    }

    @Test
    void unknownQuizReturns404ThroughAllLayers() throws Exception {
        mockMvc.perform(get("/api/quizzes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Quiz with id 999999 not found"));
        mockMvc.perform(get("/api/quizzes/999999/attempts"))
                .andExpect(status().isNotFound());
    }
}
