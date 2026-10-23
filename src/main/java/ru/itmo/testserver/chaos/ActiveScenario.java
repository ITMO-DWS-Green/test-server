package ru.itmo.testserver.chaos;

import java.time.Instant;

/**
 * Включённый сценарий.
 *
 * @param endsAt  когда вернётся NORMAL; {@code null} — бессрочно
 * @param faultId id сбоя в разметке; у NORMAL — {@code null}
 */
public record ActiveScenario(Scenario scenario, Instant startedAt, Instant endsAt, String faultId) {

    public ScenarioType type() {
        return scenario.type();
    }
}
