package ru.itmo.testserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.testserver.chaos.ChaosService;
import ru.itmo.testserver.chaos.ScenarioType;

@SpringBootTest(properties = "testserver.service-name=orders")
@AutoConfigureMockMvc
class ChaosApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ChaosService chaos;

    @AfterEach
    void reset() throws Exception {
        mvc.perform(delete("/chaos/scenario")).andExpect(status().isOk());
    }

    private void enable(String json) throws Exception {
        mvc.perform(post("/chaos/scenario").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void normalServiceAnswers200() throws Exception {
        mvc.perform(get("/api/orders/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("orders"));
    }

    @Test
    void errors5xxWithFullRateFailsEveryRequest() throws Exception {
        enable("{\"type\": \"ERRORS_5XX\", \"rate\": 1.0}");
        mvc.perform(get("/api/orders/1")).andExpect(status().isInternalServerError());
        mvc.perform(post("/api/orders")).andExpect(status().isInternalServerError());

        mvc.perform(delete("/chaos/scenario")).andExpect(status().isOk());
        mvc.perform(get("/api/orders/1")).andExpect(status().isOk());
    }

    @Test
    void errors4xxReturnsClientErrors() throws Exception {
        enable("{\"type\": \"ERRORS_4XX\", \"rate\": 1.0}");
        int code = mvc.perform(get("/api/orders/1")).andReturn().getResponse().getStatus();
        assertThat(code).isIn(400, 404, 409, 429);
    }

    @Test
    void unavailableRejectsRequestsAndHealthIsDown() throws Exception {
        enable("{\"type\": \"UNAVAILABLE\"}");
        mvc.perform(get("/api/orders/1")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        // управление продолжает работать — иначе сервис не вернуть
        mvc.perform(get("/chaos/status")).andExpect(jsonPath("$.scenario.type").value("UNAVAILABLE"));
    }

    @Test
    void latencyDelaysResponses() throws Exception {
        enable("{\"type\": \"LATENCY\", \"delayMs\": 300}");
        long start = System.nanoTime();
        mvc.perform(get("/api/orders/1")).andExpect(status().isOk());
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(300);
    }

    @Test
    void scenarioReturnsToNormalAfterDuration() throws Exception {
        enable("{\"type\": \"ERRORS_5XX\", \"rate\": 1.0, \"durationSeconds\": 1}");
        long deadline = System.currentTimeMillis() + 5_000;
        while (chaos.current().type() != ScenarioType.NORMAL && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(chaos.current().type()).isEqualTo(ScenarioType.NORMAL);
    }

    @Test
    void listsAllScenarioTypesAndPlaybooks() throws Exception {
        mvc.perform(get("/chaos/scenarios"))
                .andExpect(jsonPath("$.length()").value(ScenarioType.values().length));
        mvc.perform(get("/chaos/playbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'cascade-database')]").exists());
    }

    @Test
    void panelIsServed() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
