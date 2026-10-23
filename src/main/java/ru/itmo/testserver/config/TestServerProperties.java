package ru.itmo.testserver.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки экземпляра, префикс {@code testserver}.
 *
 * @param serviceName  имя этого сервиса — под ним он виден в log-collector
 * @param downstream   имена сервисов, которые этот сервис вызывает на каждый запрос
 * @param peers        адреса всех экземпляров: имя → базовый URL
 * @param traffic      встроенный генератор запросов к самому себе
 * @param collector    отправка данных в log-collector
 * @param playbooksDir папка со сценариями-плейбуками (JSON)
 */
@ConfigurationProperties("testserver")
public record TestServerProperties(
        String serviceName,
        List<String> downstream,
        Map<String, String> peers,
        Traffic traffic,
        Collector collector,
        String playbooksDir) {

    public TestServerProperties {
        serviceName = serviceName == null || serviceName.isBlank() ? "test-service" : serviceName;
        downstream = downstream == null ? List.of() : List.copyOf(downstream);
        peers = peers == null ? Map.of() : new LinkedHashMap<>(peers);
        traffic = traffic == null ? new Traffic(0) : traffic;
        collector = collector == null ? new Collector(null, null, null, null) : collector;
        playbooksDir = playbooksDir == null || playbooksDir.isBlank() ? "playbooks" : playbooksDir;
    }

    /** @param rps запросов в секунду к самому себе; 0 — выключено */
    public record Traffic(double rps) {
    }

    /**
     * @param url                адрес log-collector; пусто — ничего не отправляем
     * @param scenarioId         id сценария для разметки при ручном переключении
     * @param exportIntervalMs   как часто отправлять логи и метрики
     * @param crashLabelSeconds  длительность сбоя в разметке для CRASH — сам процесс конец не узнает
     */
    public record Collector(String url, String scenarioId, Long exportIntervalMs, Integer crashLabelSeconds) {
        public Collector {
            scenarioId = scenarioId == null || scenarioId.isBlank() ? "manual" : scenarioId;
            exportIntervalMs = exportIntervalMs == null ? 1000L : exportIntervalMs;
            crashLabelSeconds = crashLabelSeconds == null ? 30 : crashLabelSeconds;
        }

        public boolean enabled() {
            return url != null && !url.isBlank();
        }
    }
}
