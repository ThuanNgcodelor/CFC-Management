package com.booking.system.health;

import java.nio.file.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;

/** Fake HTTP pilot for the history activation UI. Never loads the real history key or source data. */
public final class HealthHistoryPreview {
    @Configuration(proxyBeanMethods=false)
    @Import({HealthPhase8Preview.Config.class,HealthHistoryImportTest.FixtureConfig.class})
    static class Config { }
    public static void main(String[] args) throws Exception {
        var context=new SpringApplication(Config.class).run("--spring.config.location=optional:file:/tmp/cfc-health-history-unused.properties","--spring.config.import=",
            "--server.address=127.0.0.1","--server.port=18088",
            "--spring.datasource.url=jdbc:h2:mem:health_history_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--spring.jpa.hibernate.ddl-auto=validate","--spring.jpa.show-sql=false","--spring.jpa.open-in-view=false","--spring.flyway.enabled=true",
            "--cfc.health.enabled=true","--cfc.health.history.enabled=true","--app.cors.allowed-origins=http://127.0.0.1:5198","--logging.level.root=WARN");
        var fixture=new HealthHistoryImportTest();context.getAutowireCapableBeanFactory().autowireBean(fixture);fixture.setup();
        System.out.println("HEALTH_HISTORY_SYNTHETIC_HTTP_READY port=18088 key=32_zero_bytes_for_fixture_only");
    }
}
