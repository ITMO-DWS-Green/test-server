package ru.itmo.testserver.chaos;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.itmo.testserver.config.TestServerProperties;
import ru.itmo.testserver.peers.PeerClient;
import ru.itmo.testserver.traffic.TrafficGenerator;

/** Направляет команду этому экземпляру или другому по имени сервиса. */
@Component
public class ScenarioRouter {

    private final ChaosService chaos;
    private final TrafficGenerator traffic;
    private final PeerClient peers;
    private final TestServerProperties props;
    private final Clock clock;

    public ScenarioRouter(ChaosService chaos, TrafficGenerator traffic, PeerClient peers,
                          TestServerProperties props, Clock clock) {
        this.chaos = chaos;
        this.traffic = traffic;
        this.peers = peers;
        this.props = props;
        this.clock = clock;
    }

    public boolean isSelf(String target) {
        return target == null || target.isBlank() || target.equals(props.serviceName());
    }

    public Object apply(String target, Scenario scenario) {
        return isSelf(target) ? ScenarioView.of(chaos.apply(scenario), clock) : peers.apply(target, scenario);
    }

    public Object reset(String target) {
        return isSelf(target) ? ScenarioView.of(chaos.reset(), clock) : peers.reset(target);
    }

    public Object setTraffic(String target, double rps) {
        if (isSelf(target)) {
            traffic.setRps(rps);
            return java.util.Map.of("service", props.serviceName(), "rps", traffic.rps());
        }
        return peers.setTraffic(target, rps);
    }
}
