package ru.itmo.testserver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Сбой зависимого сервиса каскадом виден у вызывающего: 502. */
@SpringBootTest(properties = {
    "testserver.service-name=gateway",
    "testserver.downstream=orders",
    "testserver.peers.orders=http://localhost:1",
})
@AutoConfigureMockMvc
class DownstreamFailureTest {

    @Autowired
    MockMvc mvc;

    @Test
    void unreachableDownstreamGives502() throws Exception {
        mvc.perform(get("/api/orders/7"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.service").value("gateway"));
    }

    @Test
    void unreachablePeerIsReportedInStatusAll() throws Exception {
        mvc.perform(get("/chaos/status/all"))
                .andExpect(jsonPath("$[?(@.service == 'orders')].reachable").value(false))
                .andExpect(jsonPath("$[?(@.service == 'gateway')].reachable").value(true));
    }
}
