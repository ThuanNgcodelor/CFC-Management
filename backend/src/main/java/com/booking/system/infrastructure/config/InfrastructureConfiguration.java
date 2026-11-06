package com.booking.system.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@org.springframework.context.annotation.PropertySource("classpath:infrastructure-defaults.properties")
@Configuration
@EnableConfigurationProperties(InfrastructureProperties.class)
public class InfrastructureConfiguration {}
