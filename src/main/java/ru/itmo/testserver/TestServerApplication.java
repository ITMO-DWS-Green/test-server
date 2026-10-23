package ru.itmo.testserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Тестовый сервер для log-collector: изображает сервис, сбоями которого можно управлять.
 * Несколько экземпляров вызывают друг друга и образуют цепочку сервисов.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class TestServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestServerApplication.class, args);
    }
}
