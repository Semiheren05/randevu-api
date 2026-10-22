package com.semih.randevuapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on @Async; Spring Boot provides the thread pool (spring.task.execution.*). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
