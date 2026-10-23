package com.semih.randevuapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs, such as the booking reminders. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
