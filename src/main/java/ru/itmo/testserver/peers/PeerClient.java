package ru.itmo.testserver.peers;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import ru.itmo.testserver.chaos.Scenario;
import ru.itmo.testserver.config.TestServerProperties;

/**
 * Управление другими экземплярами через их /chaos API. Нужно, чтобы с одной панели
 * и из одного плейбука переключать сценарии на всех сервисах.
 */
@Component
public class PeerClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;
    private final TestServerProperties props;

    public PeerClient(RestClient.Builder builder, TestServerProperties props) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.http = builder.requestFactory(factory).build();
        this.props = props;
    }

    /** Статус экземпляра; недоступный — {@code reachable=false}. */
    public Map<String, Object> status(String peer) {
        try {
            Map<String, Object> status = http.get().uri(url(peer) + "/chaos/status").retrieve().body(JSON);
            return status == null ? unreachable(peer) : status;
        } catch (RestClientException | ResponseStatusException e) {
            return unreachable(peer);
        }
    }

    public Map<String, Object> apply(String peer, Scenario scenario) {
        return call(() -> http.post().uri(url(peer) + "/chaos/scenario").body(scenario).retrieve().body(JSON), peer);
    }

    public Map<String, Object> reset(String peer) {
        return call(() -> http.delete().uri(url(peer) + "/chaos/scenario").retrieve().body(JSON), peer);
    }

    public Map<String, Object> setTraffic(String peer, double rps) {
        return call(() -> http.put().uri(url(peer) + "/chaos/traffic").body(Map.of("rps", rps))
                .retrieve().body(JSON), peer);
    }

    private String url(String peer) {
        String url = props.peers().get(peer);
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный сервис: " + peer);
        }
        return url;
    }

    private static Map<String, Object> call(java.util.function.Supplier<Map<String, Object>> request, String peer) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Сервис " + peer + " недоступен: " + e.getMessage());
        }
    }

    private static Map<String, Object> unreachable(String peer) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", peer);
        status.put("reachable", false);
        return status;
    }
}
