package ru.itmo.testserver.shop;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.testserver.config.TestServerProperties;

/**
 * «Полезная работа» сервиса. Каждый запрос вызывает все зависимые сервисы ({@code testserver.downstream}),
 * поэтому сбой в одном сервисе каскадом виден у тех, кто его вызывает.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

    private final DownstreamClient downstream;
    private final TestServerProperties props;

    public OrderController(DownstreamClient downstream, TestServerProperties props) {
        this.downstream = downstream;
        this.props = props;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable long id) {
        return process("read", id, HttpMethod.GET, "/api/orders/" + id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create() {
        long id = ThreadLocalRandom.current().nextLong(1, 100_000);
        return process("create", id, HttpMethod.POST, "/api/orders");
    }

    private ResponseEntity<Map<String, Object>> process(String operation, long id, HttpMethod method, String path) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        work(random.nextInt(5, 25));
        String traceId = MDC.get("traceId");
        for (String peer : props.downstream()) {
            try {
                downstream.call(peer, method, path, traceId);
            } catch (DownstreamException e) {
                LOG.error("Failed to {} order {}: downstream {} {}", operation, id, peer, e.describe());
                int status = e.timeout() ? 504 : 502;
                return ResponseEntity.status(status).body(Map.of(
                        "error", "downstream " + peer + " " + e.describe(),
                        "service", props.serviceName()));
            }
        }
        if (random.nextDouble() < 0.01) {
            LOG.warn("Order {} {} took longer than expected", id, operation);
        } else {
            LOG.debug("Order {} {} ok", id, operation);
        }
        return ResponseEntity.ok(Map.of("service", props.serviceName(), "orderId", id, "operation", operation));
    }

    private static void work(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
