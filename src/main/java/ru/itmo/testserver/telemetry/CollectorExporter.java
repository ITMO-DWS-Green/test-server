package ru.itmo.testserver.telemetry;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.testserver.config.TestServerProperties;
import ru.itmo.testserver.stats.RequestStats;

/**
 * Раз в секунду отправляет в log-collector логи и метрики этого сервиса.
 * Имена метрик — как в log-collector (core/.../model/Metrics.java), плюс client_error_rate.
 */
@Component
public class CollectorExporter {

    private static final int MAX_LOGS_PER_BATCH = 5_000;
    private static final double MB = 1024 * 1024;

    private final CollectorClient client;
    private final LogCapture logs;
    private final RequestStats stats;
    private final String service;
    private final OperatingSystemMXBean os =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public CollectorExporter(CollectorClient client, LogCapture logs, RequestStats stats, TestServerProperties props) {
        this.client = client;
        this.logs = logs;
        this.stats = stats;
        this.service = props.serviceName();
    }

    @Scheduled(fixedDelayString = "${testserver.collector.export-interval-ms:1000}")
    public void export() {
        List<LogCapture.Captured> captured = logs.drain(MAX_LOGS_PER_BATCH);
        if (!client.enabled()) {
            return; // всё равно забираем записи, чтобы очередь не росла
        }
        Instant now = Instant.now();
        RequestStats.Window w = stats.last();
        List<CollectorDtos.MetricEntry> metrics = new ArrayList<>();
        metrics.add(metric(now, "rps", w.requests()));
        if (w.requests() > 0) {
            metrics.add(metric(now, "latency_ms", w.avgLatencyMs()));
            metrics.add(metric(now, "error_rate", w.errorRate()));
            metrics.add(metric(now, "client_error_rate", w.clientErrorRate()));
        }
        double cpu = os.getProcessCpuLoad();
        if (cpu >= 0) {
            metrics.add(metric(now, "cpu_usage", cpu));
        }
        Runtime rt = Runtime.getRuntime();
        metrics.add(metric(now, "memory_mb", (rt.totalMemory() - rt.freeMemory()) / MB));

        List<CollectorDtos.LogEntry> entries = captured.stream()
                .map(c -> new CollectorDtos.LogEntry(c.timestamp(), service, c.level(), c.message(),
                        c.traceId(), c.attributes()))
                .toList();
        client.sendTelemetry(new CollectorDtos.TelemetryBatch(entries, metrics));
    }

    private CollectorDtos.MetricEntry metric(Instant at, String name, double value) {
        return new CollectorDtos.MetricEntry(at, service, name, value);
    }
}
