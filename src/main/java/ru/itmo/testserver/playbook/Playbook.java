package ru.itmo.testserver.playbook;

import java.util.Comparator;
import java.util.List;
import ru.itmo.testserver.chaos.Scenario;

/**
 * Сценарий-плейбук: последовательность сбоев по времени, возможно на разных сервисах.
 *
 * @param steps шаги; порядок в файле не важен, выполняются по {@code atSeconds}
 */
public record Playbook(String name, String description, List<Step> steps) {

    public Playbook {
        steps = steps == null ? List.of()
                : steps.stream().sorted(Comparator.comparingInt(Step::atSeconds)).toList();
    }

    public Playbook withName(String value) {
        return new Playbook(value, description, steps);
    }

    /** Самый поздний момент, когда что-то ещё меняется: старт шага + его длительность. */
    public int totalSeconds() {
        return steps.stream()
                .mapToInt(s -> s.atSeconds() + (s.scenario().durationSeconds() == null ? 0 : s.scenario().durationSeconds()))
                .max().orElse(0);
    }

    /**
     * @param atSeconds через сколько секунд от запуска выполнить шаг
     * @param target    имя сервиса; пусто — тот экземпляр, где запущен плейбук
     */
    public record Step(int atSeconds, String target, Scenario scenario) {
        public Step {
            atSeconds = Math.max(0, atSeconds);
            scenario = scenario == null ? Scenario.normal() : scenario;
        }
    }
}
