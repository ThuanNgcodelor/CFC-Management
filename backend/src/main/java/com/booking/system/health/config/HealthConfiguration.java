package com.booking.system.health.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:health-defaults.properties")
public class HealthConfiguration { }
