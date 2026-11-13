package com.booking.system.health;

import com.booking.system.health.config.HealthConfiguration;
import com.booking.system.health.service.HealthAccess;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class HealthDefaultsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(HealthConfiguration.class)
            .withBean(HealthAccess.class);
    @Test void shippedDefaultsDisableHealthWithoutLoadingServerConfiguration() {
        runner.withPropertyValues("HEALTH_ENABLED=false").run(context -> assertThat(context.getBean(HealthAccess.class).enabled()).isFalse());
    }
    @Test void localEnvironmentOptInEnablesHealth() {
        runner.withPropertyValues("HEALTH_ENABLED=true").run(context -> assertThat(context.getBean(HealthAccess.class).enabled()).isTrue());
    }
}
