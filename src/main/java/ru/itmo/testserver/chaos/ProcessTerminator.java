package ru.itmo.testserver.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Сценарий CRASH: процесс завершается с кодом 1 без корректной остановки, как при настоящем падении. */
@Component
public class ProcessTerminator {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessTerminator.class);

    public void terminate() {
        Thread.ofPlatform().daemon().name("crash").start(() -> {
            try {
                Thread.sleep(500); // успеть ответить на запрос, который включил сценарий
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Fatal error: process is terminating (injected crash)");
            Runtime.getRuntime().halt(1);
        });
    }
}
