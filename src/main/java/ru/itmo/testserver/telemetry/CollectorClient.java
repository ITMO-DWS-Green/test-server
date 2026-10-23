package ru.itmo.testserver.telemetry;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.itmo.testserver.config.TestServerProperties;

/** HTTP-клиент к log-collector. Пустой {@code testserver.collector.url} — отправка выключена. */
@Component
public class CollectorClient {

    private static final Logger LOG = LoggerFactory.getLogger(CollectorClient.class);

    private final RestClient http;
    private final boolean enabled;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public CollectorClient(RestClient.Builder builder, TestServerProperties props) {
        this.enabled = props.collector().enabled();
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.http = enabled
                ? builder.requestFactory(factory).baseUrl(props.collector().url()).build()
                : null;
        if (enabled) {
            LOG.info("Sending telemetry to log-collector at {}", props.collector().url());
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public void sendTelemetry(CollectorDtos.TelemetryBatch batch) {
        post("/api/v1/telemetry", batch);
    }

    public void sendLabel(CollectorDtos.FaultLabel label) {
        post("/api/v1/labels", label);
    }

    private void post(String path, Object body) {
        if (!enabled) {
            return;
        }
        try {
            http.post().uri(path).body(body).retrieve().toBodilessEntity();
            if (healthy.compareAndSet(false, true)) {
                LOG.info("log-collector is reachable again");
            }
        } catch (RestClientException e) {
            // Пишем только при смене состояния, чтобы не заваливать лог.
            if (healthy.compareAndSet(true, false)) {
                LOG.warn("log-collector is unreachable: {}", e.getMessage());
            }
        }
    }
}
