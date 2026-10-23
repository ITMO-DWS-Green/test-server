package ru.itmo.testserver.telemetry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.LoggerFactory;

/**
 * Перехватывает записи лога приложения, чтобы отправить их в log-collector.
 * Берём всё из пакета ru.itmo.testserver и WARN+ из остальных; собственные сообщения
 * о недоступности log-collector не берём.
 */
public class LogCapture extends AppenderBase<ILoggingEvent> {

    private static final String OWN_PACKAGE = "ru.itmo.testserver";

    private final BlockingQueue<Captured> queue = new ArrayBlockingQueue<>(20_000);

    @PostConstruct
    public void attach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        setName("log-collector-capture");
        start();
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @PreDestroy
    public void detach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(this);
        stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String logger = event.getLoggerName();
        boolean own = logger.startsWith(OWN_PACKAGE);
        if (!own && !event.getLevel().isGreaterOrEqual(Level.WARN)) {
            return;
        }
        if (logger.equals(CollectorClient.class.getName())) {
            return;
        }
        Map<String, String> attributes = new HashMap<>();
        attributes.put("logger", logger);
        attributes.put("thread", event.getThreadName());
        IThrowableProxy error = event.getThrowableProxy();
        if (error != null) {
            attributes.put("exception", error.getClassName());
            if (error.getMessage() != null) {
                attributes.put("exceptionMessage", error.getMessage());
            }
        }
        queue.offer(new Captured(Instant.ofEpochMilli(event.getTimeStamp()), event.getLevel().toString(),
                event.getFormattedMessage(), event.getMDCPropertyMap().get("traceId"), attributes));
    }

    /** Забрать накопленные записи, не больше {@code max}. */
    public List<Captured> drain(int max) {
        List<Captured> result = new ArrayList<>();
        queue.drainTo(result, max);
        return result;
    }

    public record Captured(Instant timestamp, String level, String message, String traceId,
                           Map<String, String> attributes) {
    }
}
