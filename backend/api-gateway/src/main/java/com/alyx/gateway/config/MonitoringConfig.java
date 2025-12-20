package com.alyx.gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Monitoring configuration for API Gateway
 * Validates: Requirements 4.1, 4.5, 6.4
 */
@Configuration
public class MonitoringConfig {

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        return new InMemoryHttpExchangeRepository();
    }

    @Bean
    public WebFilter metricsWebFilter(MeterRegistry meterRegistry) {
        return new MetricsWebFilter(meterRegistry);
    }

    private static class MetricsWebFilter implements WebFilter {
        private final MeterRegistry meterRegistry;
        private final Timer requestTimer;

        public MetricsWebFilter(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            this.requestTimer = Timer.builder("alyx.gateway.request.duration")
                .description("Gateway request processing time")
                .register(meterRegistry);
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            Timer.Sample sample = Timer.start(meterRegistry);
            
            return chain.filter(exchange)
                .doFinally(signalType -> {
                    sample.stop(requestTimer);
                    
                    // Record additional metrics
                    String path = exchange.getRequest().getPath().value();
                    String method = exchange.getRequest().getMethod().name();
                    int statusCode = exchange.getResponse().getStatusCode() != null ? 
                        exchange.getResponse().getStatusCode().value() : 0;
                    
                    meterRegistry.counter("alyx.gateway.requests.total",
                        "method", method,
                        "path", path,
                        "status", String.valueOf(statusCode))
                        .increment();
                });
        }
    }
}