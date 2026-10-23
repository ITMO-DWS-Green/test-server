package ru.itmo.testserver.chaos;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** /actuator/health показывает DOWN, пока сервис «недоступен» или «падает». */
@Component
public class ChaosHealthIndicator implements HealthIndicator {

    private final ChaosService chaos;

    public ChaosHealthIndicator(ChaosService chaos) {
        this.chaos = chaos;
    }

    @Override
    public Health health() {
        ScenarioType type = chaos.current().type();
        Health.Builder builder = type.isDown() ? Health.down() : Health.up();
        return builder.withDetail("scenario", type.name()).build();
    }
}
