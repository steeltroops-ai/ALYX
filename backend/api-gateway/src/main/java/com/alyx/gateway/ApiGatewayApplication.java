package com.alyx.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application for ALYX Distributed Orchestrator
 * 
 * Provides centralized routing, authentication, rate limiting, and monitoring
 * for all microservices in the ALYX ecosystem.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}