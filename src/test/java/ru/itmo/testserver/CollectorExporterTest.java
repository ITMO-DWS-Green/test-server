package ru.itmo.testserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.itmo.testserver.config.TestServerProperties;
import ru.itmo.testserver.stats.RequestStats;
import ru.itmo.testserver.telemetry.CollectorClient;
import ru.itmo.testserver.telemetry.CollectorExporter;
import ru.itmo.testserver.telemetry.LogCapture;

class CollectorExporterTest {

    private final CollectorClient client = mock(CollectorClient.class);

    CollectorExporterTest() {
        when(client.enabled()).thenReturn(true);
    }

    @Test
    void sendsTelemetryByDefault() {
        exporter(null).export();

        verify(client).sendTelemetry(any());
    }

    @Test
    void sendsNothingWhenTelemetryDisabled() {
        exporter(false).export();

        verify(client, never()).sendTelemetry(any());
    }

    private CollectorExporter exporter(Boolean telemetry) {
        var collector = new TestServerProperties.Collector("http://advisor:8080", null, null, null, telemetry);
        var props = new TestServerProperties("gateway", null, Map.of(), null, collector, null);
        return new CollectorExporter(client, new LogCapture(), new RequestStats(), props);
    }
}
