package ru.itmo.testserver.telemetry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryConfig {

    @Bean
    LogCapture logCapture() {
        return new LogCapture();
    }
}
