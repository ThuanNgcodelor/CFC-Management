package com.booking.system.health;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Packaged data workflow against the same disposable loopback MySQL QA schema. */
@EnabledIfSystemProperty(named="health.mysql.qa.url",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/health_qa_acceptance(?:\\?.*)?")
class HealthHistoryMysqlImportTest extends HealthHistoryImportTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry properties){HealthMysqlAcceptanceTest.mysql(properties);}
}
