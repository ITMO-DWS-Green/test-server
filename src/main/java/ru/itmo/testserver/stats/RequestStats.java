package ru.itmo.testserver.stats;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Считает запросы к /api по секундным окнам: число, ошибки, задержка. */
@Component
public class RequestStats {

    private final LongAdder requests = new LongAdder();
    private final LongAdder clientErrors = new LongAdder();
    private final LongAdder serverErrors = new LongAdder();
    private final LongAdder latencySum = new LongAdder();
    private final AtomicLong latencyMax = new AtomicLong();
    private volatile Window last = new Window(Instant.now(), 0, 0, 0, 0, 0);

    public void record(int status, long latencyMs) {
        requests.increment();
        latencySum.add(latencyMs);
        latencyMax.accumulateAndGet(latencyMs, Math::max);
        if (status >= 500) {
            serverErrors.increment();
        } else if (status >= 400) {
            clientErrors.increment();
        }
    }

    @Scheduled(fixedRate = 1000)
    public void roll() {
        long count = requests.sumThenReset();
        long sum = latencySum.sumThenReset();
        last = new Window(Instant.now(), count, clientErrors.sumThenReset(), serverErrors.sumThenReset(),
                count == 0 ? 0 : (double) sum / count, latencyMax.getAndSet(0));
    }

    /** Последнее закрытое секундное окно. */
    public Window last() {
        return last;
    }

    public record Window(Instant at, long requests, long clientErrors, long serverErrors,
                         double avgLatencyMs, long maxLatencyMs) {

        public double errorRate() {
            return requests == 0 ? 0 : (double) serverErrors / requests;
        }

        public double clientErrorRate() {
            return requests == 0 ? 0 : (double) clientErrors / requests;
        }
    }
}
