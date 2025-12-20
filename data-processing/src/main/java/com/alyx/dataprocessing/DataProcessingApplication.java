package com.alyx.dataprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for ALYX Data Processing module.
 * Handles collision event data models and database operations.
 */
@SpringBootApplication
@EnableTransactionManagement
public class DataProcessingApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DataProcessingApplication.class, args);
    }
}