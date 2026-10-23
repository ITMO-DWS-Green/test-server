package ru.itmo.testserver.chaos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Текущий сценарий для API и панели. */
public record ScenarioView(
        ScenarioType type,
        String description,
        Double rate,
        Integer delayMs,
        Integer intensity,
        Integer durationSeconds,
        Instant startedAt,
        Instant endsAt,
        Long secondsLeft,
        String scenarioId,
        String faultId) {

    public static ScenarioView of(ActiveScenario a, Clock clock) {
        Scenario s = a.scenario();
        Long left = a.endsAt() == null ? null
                : Math.max(0, Duration.between(clock.instant(), a.endsAt()).toSeconds());
        return new ScenarioView(s.type(), s.type().description(), s.rate(), s.delayMs(), s.intensity(),
                s.durationSeconds(), a.startedAt(), a.endsAt(), left, s.scenarioId(), a.faultId());
    }
}
