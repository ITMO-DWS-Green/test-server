package ru.itmo.testserver.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** JSON-контракт log-collector (docs/architecture/modules.md в репозитории log-collector). */
public final class CollectorDtos {

    private CollectorDtos() {
    }

    public record TelemetryBatch(List<LogEntry> logs, List<MetricEntry> metrics) {
    }

    public record LogEntry(Instant timestamp, String service, String level, String message,
                           String traceId, Map<String, String> attributes) {
    }

    public record MetricEntry(Instant timestamp, String service, String metric, double value) {
    }

    /** Разметка сбоя: эталон, с которым log-collector сравнивает свои инциденты. */
    public record FaultLabel(String scenarioId, String faultId, String type, String source,
                             Instant start, Instant end) {
    }
}
