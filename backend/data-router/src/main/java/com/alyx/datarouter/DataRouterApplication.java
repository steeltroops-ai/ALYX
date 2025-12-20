package com.alyx.datarouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataRouterApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataRouterApplication.class, args);
    }
}