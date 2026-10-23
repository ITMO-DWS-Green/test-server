package ru.itmo.testserver.chaos;

import java.util.Set;

/**
 * Что сейчас происходит с сервисом.
 * {@code faultType} — соответствующий тип сбоя в log-collector (FaultType), он попадает в разметку.
 */
public enum ScenarioType {
    NORMAL("Работает нормально", null, Set.of(), 0, 0, null),
    ERRORS_5XX("Отвечает 5xx на долю запросов", "ERROR_BURST", Set.of(Param.RATE), 0, 0, null),
    ERRORS_4XX("Отвечает 4xx на долю запросов", "ERROR_BURST", Set.of(Param.RATE), 0, 0, null),
    LATENCY("Отвечает с задержкой", "LATENCY_SPIKE", Set.of(Param.DELAY_MS), 2000, 0, null),
    HANG("Запросы зависают", "LATENCY_SPIKE", Set.of(Param.DELAY_MS), 60000, 0, null),
    UNAVAILABLE("Недоступен: 503 на всё, health DOWN", "SERVICE_DOWN", Set.of(), 0, 0, null),
    CRASH("Процесс падает; в Docker перезапустится", "SERVICE_DOWN", Set.of(), 0, 0, null),
    CPU_LOAD("Нагружает процессор", "RESOURCE_LEAK", Set.of(Param.INTENSITY), 0, 2, "потоков"),
    MEMORY_LEAK("Утечка памяти", "RESOURCE_LEAK", Set.of(Param.INTENSITY), 0, 10, "МБ в секунду"),
    LOG_FLOOD("Сыплет ошибками в лог", "ERROR_BURST", Set.of(Param.INTENSITY), 0, 50, "строк в секунду");

    public enum Param { RATE, DELAY_MS, INTENSITY }

    private final String description;
    private final String faultType;
    private final Set<Param> params;
    private final int defaultDelayMs;
    private final int defaultIntensity;
    private final String intensityUnit;

    ScenarioType(String description, String faultType, Set<Param> params,
                 int defaultDelayMs, int defaultIntensity, String intensityUnit) {
        this.description = description;
        this.faultType = faultType;
        this.params = params;
        this.defaultDelayMs = defaultDelayMs;
        this.defaultIntensity = defaultIntensity;
        this.intensityUnit = intensityUnit;
    }

    public String description() {
        return description;
    }

    public String faultType() {
        return faultType;
    }

    public Set<Param> params() {
        return params;
    }

    public int defaultDelayMs() {
        return defaultDelayMs;
    }

    public int defaultIntensity() {
        return defaultIntensity;
    }

    public String intensityUnit() {
        return intensityUnit;
    }

    /** Отвечает ли сервис на запросы вообще. */
    public boolean isDown() {
        return this == UNAVAILABLE || this == CRASH;
    }
}
