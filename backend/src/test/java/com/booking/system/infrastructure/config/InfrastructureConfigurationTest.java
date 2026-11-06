package com.booking.system.infrastructure.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.assertj.core.api.Assertions.*;

class InfrastructureConfigurationTest {
    @Test void shipsDisabledDefaultsWithoutIgnoredApplicationFiles() {
        try (var context = new AnnotationConfigApplicationContext(InfrastructureConfiguration.class)) {
            var properties = context.getBean(InfrastructureProperties.class);
            assertThat(properties.monitoringEnabled()).isFalse();
            assertThat(properties.routerBaseUrl()).isEqualTo("https://192.168.1.1");
            assertThat(properties.routerPassword()).isEmpty();
        }
    }
    @Test void bindsLegacyEnvironmentAliasesForProductionCompatibility() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-aliases", Map.of(
                    "CFC_INFRA_ROUTER_BASE_URL", "https://10.0.0.1", "CFC_INFRA_ROUTER_USERNAME", "reader",
                    "CFC_INFRA_ROUTER_PASSWORD", "synthetic-only", "CFC_INFRA_MONITORING_ENABLED", "true")));
            context.register(InfrastructureConfiguration.class); context.refresh();
            var properties = context.getBean(InfrastructureProperties.class);
            assertThat(properties.monitoringEnabled()).isTrue();
            assertThat(properties.routerBaseUrl()).isEqualTo("https://10.0.0.1");
            assertThat(properties.routerPassword()).isEqualTo("synthetic-only");
            assertThat(properties.toString()).doesNotContain("synthetic-only");
        }
    }
}
