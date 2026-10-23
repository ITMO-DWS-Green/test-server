package ru.itmo.testserver.chaos;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.testserver.config.TestServerProperties;
import ru.itmo.testserver.peers.PeerClient;
import ru.itmo.testserver.stats.RequestStats;
import ru.itmo.testserver.traffic.TrafficGenerator;

/**
 * Управление сценариями. Параметр {@code target} — имя другого сервиса из {@code testserver.peers};
 * без него команда относится к этому экземпляру.
 */
@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private final ChaosService chaos;
    private final ScenarioRouter router;
    private final TrafficGenerator traffic;
    private final RequestStats stats;
    private final PeerClient peers;
    private final TestServerProperties props;
    private final Clock clock;

    public ChaosController(ChaosService chaos, ScenarioRouter router, TrafficGenerator traffic,
                           RequestStats stats, PeerClient peers, TestServerProperties props, Clock clock) {
        this.chaos = chaos;
        this.router = router;
        this.traffic = traffic;
        this.stats = stats;
        this.peers = peers;
        this.props = props;
        this.clock = clock;
    }

    public record ServiceStatus(String service, boolean reachable, ScenarioView scenario, double trafficRps,
                                RequestStats.Window stats, List<String> downstream, long uptimeSeconds) {
    }

    public record ScenarioInfo(ScenarioType type, String description, Set<ScenarioType.Param> params,
                               int defaultDelayMs, int defaultIntensity, String intensityUnit, String faultType) {
    }

    public record TrafficRequest(double rps) {
    }

    @GetMapping("/status")
    public ServiceStatus status() {
        return new ServiceStatus(props.serviceName(), true, ScenarioView.of(chaos.current(), clock), traffic.rps(),
                stats.last(), props.downstream(), ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
    }

    /** Статусы всех сервисов из testserver.peers — для панели. */
    @GetMapping("/status/all")
    public List<Object> statusAll() {
        List<Object> result = new ArrayList<>();
        boolean selfListed = false;
        for (String peer : props.peers().keySet()) {
            if (router.isSelf(peer)) {
                result.add(status());
                selfListed = true;
            } else {
                result.add(peers.status(peer));
            }
        }
        if (!selfListed) {
            result.add(0, status());
        }
        return result;
    }

    @GetMapping("/scenarios")
    public List<ScenarioInfo> scenarios() {
        return Arrays.stream(ScenarioType.values())
                .map(t -> new ScenarioInfo(t, t.description(), t.params(), t.defaultDelayMs(),
                        t.defaultIntensity(), t.intensityUnit(), t.faultType()))
                .toList();
    }

    @PostMapping("/scenario")
    public Object apply(@RequestBody Scenario scenario, @RequestParam(required = false) String target) {
        return router.apply(target, scenario);
    }

    @DeleteMapping("/scenario")
    public Object reset(@RequestParam(required = false) String target) {
        return router.reset(target);
    }

    @PutMapping("/traffic")
    public Object traffic(@RequestBody TrafficRequest request, @RequestParam(required = false) String target) {
        return router.setTraffic(target, request.rps());
    }

    @GetMapping("/peers")
    public Map<String, String> peers() {
        return props.peers();
    }
}
