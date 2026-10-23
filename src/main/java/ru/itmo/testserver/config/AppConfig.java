package ru.itmo.testserver.config;

import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import ru.itmo.testserver.chaos.ChaosFilter;
import ru.itmo.testserver.chaos.ChaosService;
import ru.itmo.testserver.stats.RequestStats;
import ru.itmo.testserver.stats.RequestStatsFilter;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Внешний фильтр: traceId, учёт статуса и времени каждого запроса к /api, журнал запросов. */
    @Bean
    FilterRegistrationBean<RequestStatsFilter> requestStatsFilter(RequestStats stats) {
        var bean = new FilterRegistrationBean<>(new RequestStatsFilter(stats));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    /** Внутренний фильтр: применяет текущий сценарий сбоя к запросам /api. */
    @Bean
    FilterRegistrationBean<ChaosFilter> chaosFilter(ChaosService chaos) {
        var bean = new FilterRegistrationBean<>(new ChaosFilter(chaos));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
