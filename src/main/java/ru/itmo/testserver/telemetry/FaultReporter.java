package ru.itmo.testserver.telemetry;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.itmo.testserver.chaos.ActiveScenario;
import ru.itmo.testserver.config.TestServerProperties;

/** Отправляет разметку завершившегося сбоя в log-collector: начало, конец, тип, сервис-источник. */
@Component
public class FaultReporter {

    private static final Logger LOG = LoggerFactory.getLogger(FaultReporter.class);

    private final CollectorClient client;
    private final TestServerProperties props;

    public FaultReporter(CollectorClient client, TestServerProperties props) {
        this.client = client;
        this.props = props;
    }

    public void report(ActiveScenario fault, Instant end) {
        String scenarioId = fault.scenario().scenarioId() != null
                ? fault.scenario().scenarioId()
                : props.collector().scenarioId();
        var label = new CollectorDtos.FaultLabel(scenarioId, fault.faultId(), fault.type().faultType(),
                props.serviceName(), fault.startedAt(), end);
        LOG.info("Fault label: scenario={}, fault={}, type={} ({}), {} .. {}",
                scenarioId, fault.faultId(), label.type(), fault.type(), label.start(), label.end());
        client.sendLabel(label);
    }
}
