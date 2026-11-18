package com.booking.system.health;

import com.booking.system.entity.User;
import com.booking.system.enums.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:health_rollout_gate;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true",
 "cfc.health.enabled=false","cfc.health.self-service.enabled=false","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class HealthRolloutGateTest {
    @jakarta.annotation.Resource org.springframework.test.web.servlet.MockMvc mvc;
    @jakarta.annotation.Resource com.booking.system.security.JwtUtils tokens;
    @jakarta.annotation.Resource com.booking.system.repository.UserRepository users;
    @jakarta.annotation.Resource org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void closedReleaseHasSchemaButCannotOpenManagementOrPersonalContent() throws Exception {
        var user=new User();user.setId("gate-fixture");user.setEmail("gate@example.test");user.setRole(RoleEnum.ADMIN);user.setStatus(UserStatus.ACTIVE);
        when(tokens.validateJwtToken("gate")).thenReturn(true);when(tokens.getEmailFromJwtToken("gate")).thenReturn(user.getEmail());when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        mvc.perform(get("/api/v1/hr/health/capabilities").header("Authorization","Bearer gate")).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(get("/api/v1/hr/health/campaigns").header("Authorization","Bearer gate")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/health-self/capabilities")).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store, private"));
        mvc.perform(get("/api/v1/health-self/profile").param("from","2025").param("to","2026")).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_publications",Integer.class)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='39' AND success=TRUE",Integer.class)).isEqualTo(1);
    }
}
