package ru.itmo.testserver.traffic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.testserver.config.TestServerProperties;

/**
 * Встроенный трафик: шлёт запросы к собственному /api, чтобы сервису было что обрабатывать
 * без внешних клиентов. Обычно включается только на входном сервисе (gateway) —
 * дальше запросы идут по цепочке.
 */
@Component
public class TrafficGenerator implements ApplicationListener<WebServerInitializedEvent>, DisposableBean {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile double rps;
    private volatile int port = -1;
    private double tokens;

    public TrafficGenerator(TestServerProperties props) {
        this.rps = Math.max(0, props.traffic().rps());
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
    }

    public double rps() {
        return rps;
    }

    public void setRps(double value) {
        this.rps = Math.max(0, Math.min(value, 500));
    }

    @Scheduled(fixedRate = 100)
    public synchronized void tick() {
        if (rps <= 0 || port < 0) {
            tokens = 0;
            return;
        }
        tokens += rps / 10;
        while (tokens >= 1) {
            tokens -= 1;
            executor.submit(this::fire);
        }
    }

    private void fire() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String base = "http://localhost:" + port + "/api/orders";
        HttpRequest request = random.nextDouble() < 0.2
                ? HttpRequest.newBuilder(URI.create(base)).POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(5)).build()
                : HttpRequest.newBuilder(URI.create(base + "/" + random.nextInt(1, 100_000)))
                        .timeout(Duration.ofSeconds(5)).build();
        try {
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // результат виден в статистике сервера; клиенту он не важен
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
