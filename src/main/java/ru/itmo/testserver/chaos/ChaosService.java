package ru.itmo.testserver.chaos;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.itmo.testserver.config.TestServerProperties;
import ru.itmo.testserver.telemetry.FaultReporter;

/**
 * Хранит текущий сценарий и переключает его. При смене сценария закрывает предыдущий сбой
 * и отправляет его разметку в log-collector.
 */
@Service
public class ChaosService {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosService.class);

    private final ResourceEffects effects;
    private final FaultReporter reporter;
    private final ProcessTerminator terminator;
    private final TestServerProperties props;
    private final Clock clock;
    private final AtomicReference<ActiveScenario> active;

    public ChaosService(ResourceEffects effects, FaultReporter reporter, ProcessTerminator terminator,
                        TestServerProperties props, Clock clock) {
        this.effects = effects;
        this.reporter = reporter;
        this.terminator = terminator;
        this.props = props;
        this.clock = clock;
        this.active = new AtomicReference<>(new ActiveScenario(Scenario.normal(), clock.instant(), null, null));
    }

    public ActiveScenario current() {
        return active.get();
    }

    public synchronized ActiveScenario apply(Scenario scenario) {
        Instant now = clock.instant();
        finish(active.get(), now);

        Instant endsAt = scenario.durationSeconds() == null ? null : now.plusSeconds(scenario.durationSeconds());
        String faultId = scenario.type() == ScenarioType.NORMAL
                ? null
                : props.serviceName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        ActiveScenario next = new ActiveScenario(scenario, now, endsAt, faultId);
        active.set(next);

        if (scenario.type() == ScenarioType.NORMAL) {
            LOG.info("Scenario switched to NORMAL");
        } else {
            LOG.warn("Scenario {} started: rate={}, delayMs={}, intensity={}, duration={}s",
                    scenario.type(), scenario.rate(), scenario.delayMs(), scenario.intensity(),
                    scenario.durationSeconds() == null ? "-" : scenario.durationSeconds());
        }

        effects.start(scenario);
        if (scenario.type() == ScenarioType.CRASH) {
            // Конец сбоя процесс уже не увидит — отправляем разметку заранее.
            reporter.report(next, now.plusSeconds(props.collector().crashLabelSeconds()));
            terminator.terminate();
        }
        return next;
    }

    public ActiveScenario reset() {
        return apply(Scenario.normal());
    }

    /** Возвращает NORMAL, когда истекла длительность сценария. */
    @Scheduled(fixedDelay = 500)
    public synchronized void expire() {
        ActiveScenario current = active.get();
        if (current.endsAt() != null && !clock.instant().isBefore(current.endsAt())) {
            LOG.info("Scenario {} finished after {}s", current.type(), current.scenario().durationSeconds());
            reset();
        }
    }

    private void finish(ActiveScenario previous, Instant now) {
        effects.stopAll();
        ScenarioType type = previous.type();
        if (type != ScenarioType.NORMAL && type != ScenarioType.CRASH) {
            reporter.report(previous, now);
        }
    }
}
