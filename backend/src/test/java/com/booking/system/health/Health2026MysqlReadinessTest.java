package com.booking.system.health;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Reuses the 2026 workflow only inside the isolated MySQL acceptance container. */
@EnabledIfSystemProperty(named="health.mysql.qa.url",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/health_qa_acceptance(?:\\?.*)?")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Health2026MysqlReadinessTest extends Health2026ReadinessTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry properties) {
        HealthMysqlAcceptanceTest.mysql(properties);
    }
    @AfterAll void leaveSyntheticPublicationsForRestoreDrill() throws Exception {
        setup();
        confirmAll(stage(2026,true,List.of(Health2026WorkbookFixtures.entry(2026,"HEALTHDEV01","24","<0.5"))));
        release();
    }
}
