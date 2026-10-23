package ru.itmo.testserver.chaos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Применяет текущий сценарий к запросам /api: задержка, ошибка или отказ. */
public class ChaosFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosFilter.class);
    private static final int[] CLIENT_ERRORS = {400, 404, 409, 429};
    private static final String[] SERVER_FAILURES = {
        "Connection reset by peer while reading from database",
        "NullPointerException in OrderMapper.toDto",
        "Deadlock detected while updating order status",
        "Pool exhausted: timeout waiting for connection",
    };

    private final ChaosService chaos;

    public ChaosFilter(ChaosService chaos) {
        this.chaos = chaos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Scenario s = chaos.current().scenario();
        String target = request.getMethod() + " " + request.getRequestURI();
        switch (s.type()) {
            case UNAVAILABLE, CRASH -> {
                reject(response, 503, "service unavailable");
                return;
            }
            case LATENCY, HANG -> sleep(s.delayMs());
            case ERRORS_5XX -> {
                if (hit(s.rate())) {
                    String reason = SERVER_FAILURES[ThreadLocalRandom.current().nextInt(SERVER_FAILURES.length)];
                    LOG.error("Unhandled exception while processing {}", target, new IllegalStateException(reason));
                    reject(response, 500, "internal server error");
                    return;
                }
            }
            case ERRORS_4XX -> {
                if (hit(s.rate())) {
                    int code = CLIENT_ERRORS[ThreadLocalRandom.current().nextInt(CLIENT_ERRORS.length)];
                    LOG.warn("Rejected {}: {}", target, reasonOf(code));
                    reject(response, code, reasonOf(code));
                    return;
                }
            }
            default -> {
                // NORMAL и сценарии, влияющие на ресурсы, а не на ответы
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean hit(double rate) {
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String reasonOf(int code) {
        return switch (code) {
            case 400 -> "validation failed: field 'quantity' must be positive";
            case 404 -> "order not found";
            case 409 -> "order is already being processed";
            case 429 -> "rate limit exceeded";
            default -> "client error";
        };
    }

    private static void reject(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
