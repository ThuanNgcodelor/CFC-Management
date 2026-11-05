package com.booking.system.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfrastructureProperties.class)
public class InfrastructureConfiguration {}
