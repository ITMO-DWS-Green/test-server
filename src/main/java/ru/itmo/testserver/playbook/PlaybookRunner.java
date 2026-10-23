package ru.itmo.testserver.playbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.itmo.testserver.chaos.ScenarioRouter;
import ru.itmo.testserver.config.TestServerProperties;

/**
 * Читает плейбуки из папки {@code testserver.playbooks-dir} (заново при каждом запросе — можно
 * добавлять файлы без перезапуска) и выполняет один плейбук за раз.
 */
@Component
public class PlaybookRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PlaybookRunner.class);
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ScenarioRouter router;
    private final ObjectMapper json;
    private final TestServerProperties props;
    private final Clock clock;
    private volatile Run current;

    public PlaybookRunner(ScenarioRouter router, ObjectMapper json, TestServerProperties props, Clock clock) {
        this.router = router;
        this.json = json;
        this.props = props;
        this.clock = clock;
    }

    public Map<String, Playbook> list() {
        Map<String, Playbook> result = new TreeMap<>();
        Path dir = Path.of(props.playbooksDir());
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : files) {
                String fallbackName = file.getFileName().toString().replaceFirst("\\.json$", "");
                try (InputStream in = Files.newInputStream(file)) {
                    Playbook playbook = json.readValue(in, Playbook.class);
                    String name = playbook.name() == null || playbook.name().isBlank() ? fallbackName : playbook.name();
                    result.put(name, playbook.withName(name));
                } catch (IOException e) {
                    LOG.warn("Playbook {} is invalid: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warn("Cannot read playbooks from {}: {}", dir, e.getMessage());
        }
        return result;
    }

    public synchronized RunView start(String name) {
        Playbook playbook = list().get(name);
        if (playbook == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Нет плейбука " + name);
        }
        stop();
        Instant startedAt = clock.instant();
        String scenarioId = name + "-" + ID_TIME.format(startedAt);
        Run run = new Run(playbook, scenarioId, startedAt);
        run.thread = Thread.ofVirtual().name("playbook-" + name).start(() -> execute(run));
        current = run;
        LOG.warn("Playbook {} started, scenarioId={}", name, scenarioId);
        return run.view(clock);
    }

    /** Остановить плейбук и вернуть NORMAL на всех сервисах, которых он коснулся. */
    public synchronized RunView stop() {
        Run run = current;
        if (run == null || !run.running) {
            return run == null ? null : run.view(clock);
        }
        run.running = false;
        run.thread.interrupt();
        for (String target : run.touched) {
            try {
                router.reset(target);
            } catch (RuntimeException e) {
                LOG.warn("Cannot reset {} after playbook: {}", target, e.getMessage());
            }
        }
        LOG.info("Playbook {} stopped", run.playbook.name());
        return run.view(clock);
    }

    public RunView current() {
        Run run = current;
        return run == null ? null : run.view(clock);
    }

    private void execute(Run run) {
        try {
            for (Playbook.Step step : run.playbook.steps()) {
                long waitMs = run.startedAt.plusSeconds(step.atSeconds()).toEpochMilli() - clock.millis();
                if (waitMs > 0) {
                    Thread.sleep(waitMs);
                }
                if (!run.running) {
                    return;
                }
                String target = router.isSelf(step.target()) ? props.serviceName() : step.target();
                try {
                    router.apply(target, step.scenario().withScenarioId(run.scenarioId));
                    run.touched.add(target);
                    LOG.info("Playbook {}: {} on {}", run.playbook.name(), step.scenario().type(), target);
                } catch (RuntimeException e) {
                    LOG.warn("Playbook {}: step on {} failed: {}", run.playbook.name(), target, e.getMessage());
                }
                run.done.incrementAndGet();
            }
            // Ждём, пока закончатся длительности последних шагов, — плейбук считается идущим до конца.
            long tailMs = run.startedAt.plusSeconds(run.playbook.totalSeconds()).toEpochMilli() - clock.millis();
            if (tailMs > 0) {
                Thread.sleep(tailMs);
            }
            run.running = false;
            LOG.info("Playbook {} finished", run.playbook.name());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Run {
        final Playbook playbook;
        final String scenarioId;
        final Instant startedAt;
        final AtomicInteger done = new AtomicInteger();
        final Set<String> touched = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
        volatile boolean running = true;
        Thread thread;

        Run(Playbook playbook, String scenarioId, Instant startedAt) {
            this.playbook = playbook;
            this.scenarioId = scenarioId;
            this.startedAt = startedAt;
        }

        RunView view(Clock clock) {
            long elapsed = Math.max(0, (clock.millis() - startedAt.toEpochMilli()) / 1000);
            return new RunView(playbook.name(), scenarioId, startedAt, running, done.get(),
                    playbook.steps().size(), elapsed, playbook.totalSeconds());
        }
    }

    public record RunView(String playbook, String scenarioId, Instant startedAt, boolean running,
                          int stepsDone, int stepsTotal, long elapsedSeconds, int totalSeconds) {
    }
}
