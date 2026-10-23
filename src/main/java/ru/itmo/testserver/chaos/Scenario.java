package ru.itmo.testserver.chaos;

/**
 * Сценарий, который нужно включить. Незаданные параметры берутся по умолчанию для типа.
 *
 * @param rate            доля сбойных ответов для ERRORS_5XX / ERRORS_4XX, от 0 до 1
 * @param delayMs         задержка для LATENCY / HANG
 * @param durationSeconds через сколько секунд вернуться в NORMAL; {@code null} — пока не выключат
 * @param intensity       сила для CPU_LOAD / MEMORY_LEAK / LOG_FLOOD, единицы — в {@link ScenarioType#intensityUnit()}
 * @param scenarioId      id сценария для разметки; {@code null} — из настроек
 */
public record Scenario(
        ScenarioType type,
        Double rate,
        Integer delayMs,
        Integer durationSeconds,
        Integer intensity,
        String scenarioId) {

    public Scenario {
        type = type == null ? ScenarioType.NORMAL : type;
        rate = rate == null ? 0.5 : Math.max(0, Math.min(1, rate));
        delayMs = delayMs == null ? type.defaultDelayMs() : Math.max(0, delayMs);
        durationSeconds = durationSeconds == null || durationSeconds <= 0 ? null : durationSeconds;
        intensity = intensity == null ? type.defaultIntensity() : Math.max(0, intensity);
    }

    public static Scenario normal() {
        return new Scenario(ScenarioType.NORMAL, null, null, null, null, null);
    }

    public static Scenario of(ScenarioType type) {
        return new Scenario(type, null, null, null, null, null);
    }

    public Scenario withScenarioId(String id) {
        return new Scenario(type, rate, delayMs, durationSeconds, intensity, id);
    }
}
