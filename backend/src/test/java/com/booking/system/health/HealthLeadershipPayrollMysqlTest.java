package com.booking.system.health;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfSystemProperty(named="health.mysql.qa.url",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/health_qa_acceptance(?:\\?.*)?")
class HealthLeadershipPayrollMysqlTest extends HealthLeadershipPayrollPersistenceTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry){HealthMysqlAcceptanceTest.mysql(registry);}
}
