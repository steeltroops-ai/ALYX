package com.alyx.jobscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class JobSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobSchedulerApplication.class, args);
    }
}