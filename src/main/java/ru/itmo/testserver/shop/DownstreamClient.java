package ru.itmo.testserver.shop;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.itmo.testserver.config.TestServerProperties;
import ru.itmo.testserver.stats.RequestStatsFilter;

/** Вызывает зависимые сервисы, передавая traceId. Таймаут 3 секунды — дольше считаем сбоем. */
@Component
public class DownstreamClient {

    private final RestClient http;
    private final TestServerProperties props;

    public DownstreamClient(RestClient.Builder builder, TestServerProperties props) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = builder.requestFactory(factory).build();
        this.props = props;
    }

    public void call(String peer, HttpMethod method, String path, String traceId) throws DownstreamException {
        String base = props.peers().get(peer);
        if (base == null) {
            throw new DownstreamException(peer, 0, false, "unknown service, add it to testserver.peers");
        }
        try {
            var request = http.method(method).uri(base + path);
            if (traceId != null) {
                request = request.header(RequestStatsFilter.TRACE_HEADER, traceId);
            }
            request.retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new DownstreamException(peer, e.getStatusCode().value(), false, e.getStatusText());
        } catch (ResourceAccessException e) {
            throw new DownstreamException(peer, 0, isTimeout(e), String.valueOf(e.getMostSpecificCause().getMessage()));
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
