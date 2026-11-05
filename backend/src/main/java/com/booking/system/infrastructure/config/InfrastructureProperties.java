package com.booking.system.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cfc.infrastructure")
public record InfrastructureProperties(boolean monitoringEnabled, String routerBaseUrl, String routerUsername,
        String routerPassword, String lokiBaseUrl, long pollIntervalMs) {
    public InfrastructureProperties {
        pollIntervalMs = pollIntervalMs < 15000 ? 60000 : Math.min(pollIntervalMs, 300000);
    }
}
