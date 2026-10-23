package ru.itmo.testserver.stats;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Для каждого запроса к /api: сквозной traceId (заголовок {@code X-Trace-Id}),
 * строка в журнале запросов, учёт в {@link RequestStats}.
 */
public class RequestStatsFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final Logger ACCESS = LoggerFactory.getLogger("ru.itmo.testserver.access");

    private final RequestStats stats;

    public RequestStatsFilter(RequestStats stats) {
        this.stats = stats;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong());
        }
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_HEADER, traceId);
        long start = System.nanoTime();
        int status = 500;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            stats.record(status, ms);
            String method = request.getMethod();
            String uri = request.getRequestURI();
            if (status >= 500) {
                ACCESS.error("{} {} {} {}ms", method, uri, status, ms);
            } else if (status >= 400) {
                ACCESS.warn("{} {} {} {}ms", method, uri, status, ms);
            } else {
                ACCESS.info("{} {} {} {}ms", method, uri, status, ms);
            }
            MDC.remove("traceId");
        }
    }
}
