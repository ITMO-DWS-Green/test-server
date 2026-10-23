package ru.itmo.testserver.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/** Сценарии, которые действуют на ресурсы процесса, а не на ответы: CPU_LOAD, MEMORY_LEAK, LOG_FLOOD. */
@Component
public class ResourceEffects implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceEffects.class);
    private static final int MB = 1024 * 1024;
    private static final String[] FLOOD_MESSAGES = {
        "Failed to deliver message to queue 'orders.events'",
        "Retry limit exceeded for payment callback",
        "Cache miss storm: key space 'order:*' evicted",
        "Unexpected response from inventory: HTTP 500",
    };

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "resource-effects");
        t.setDaemon(true);
        return t;
    });
    private final List<byte[]> leaked = new ArrayList<>();
    private AtomicBoolean cpuRunning = new AtomicBoolean(false);
    private ScheduledFuture<?> task;

    public synchronized void start(Scenario scenario) {
        stopAll();
        switch (scenario.type()) {
            case CPU_LOAD -> startCpu(scenario.intensity());
            case MEMORY_LEAK -> task = scheduler.scheduleAtFixedRate(
                    () -> leak(scenario.intensity()), 0, 1, TimeUnit.SECONDS);
            case LOG_FLOOD -> task = scheduler.scheduleAtFixedRate(
                    () -> flood(scenario.intensity()), 0, 1, TimeUnit.SECONDS);
            default -> {
                // остальные сценарии ресурсы не трогают
            }
        }
    }

    public synchronized void stopAll() {
        cpuRunning.set(false);
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        synchronized (leaked) {
            if (!leaked.isEmpty()) {
                LOG.info("Released {} MB of leaked memory", leaked.size());
                leaked.clear();
            }
        }
    }

    private void startCpu(int threads) {
        AtomicBoolean running = new AtomicBoolean(true);
        cpuRunning = running;
        for (int i = 0; i < threads; i++) {
            Thread.ofPlatform().daemon().name("cpu-burn-" + i).start(() -> {
                double x = 0;
                while (running.get()) {
                    x += Math.sqrt(x + 1);
                }
            });
        }
        LOG.warn("High CPU load: {} busy threads", threads);
    }

    private void leak(int mbPerSecond) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long limit = (long) (rt.maxMemory() * 0.9);
        synchronized (leaked) {
            if (used + (long) mbPerSecond * MB > limit) {
                LOG.error("Memory almost exhausted: {} MB used of {} MB, cache size {} MB",
                        used / MB, rt.maxMemory() / MB, leaked.size());
                return;
            }
            for (int i = 0; i < mbPerSecond; i++) {
                leaked.add(new byte[MB]);
            }
            LOG.warn("Session cache grew to {} MB", leaked.size());
        }
    }

    private void flood(int linesPerSecond) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < linesPerSecond; i++) {
            String message = FLOOD_MESSAGES[random.nextInt(FLOOD_MESSAGES.length)];
            LOG.error("{} (attempt {})", message, random.nextInt(1, 6));
        }
    }

    @Override
    public void destroy() {
        stopAll();
        scheduler.shutdownNow();
    }
}
