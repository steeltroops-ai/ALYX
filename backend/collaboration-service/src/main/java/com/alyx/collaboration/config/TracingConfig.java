package com.alyx.collaboration.config;

import brave.sampler.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for distributed tracing with Jaeger
 */
@Configuration
public class TracingConfig {

    @Value("${management.tracing.sampling.probability:1.0}")
    private float samplingProbability;

    @Bean
    public Sampler alwaysSampler() {
        return Sampler.create(samplingProbability);
    }
}