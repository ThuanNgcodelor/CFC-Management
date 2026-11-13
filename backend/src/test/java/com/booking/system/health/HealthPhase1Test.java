package com.booking.system.health;

import com.booking.system.config.SecurityConfig;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.health.api.*;
import com.booking.system.health.config.HealthConfiguration;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.api.HrActorResolver;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtAuthFilter;
import com.booking.system.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = HealthPhase1Test.Config.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:health_phase1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false", "spring.flyway.enabled=true",
    "cfc.health.enabled=true", "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureMockMvc
@Transactional
class HealthPhase1Test {
    @jakarta.annotation.Resource MockMvc mvc;
    @jakarta.annotation.Resource JwtUtils tokens;
    @jakarta.annotation.Resource UserRepository users;
    @jakarta.annotation.Resource HealthCampaignRepository campaigns;
    @jakarta.annotation.Resource HealthIndicatorRepository indicators;
    @jakarta.annotation.Resource HealthAuditRepository audit;
    @jakarta.annotation.Resource HealthCampaignService campaignService;
    @jakarta.annotation.Resource JdbcTemplate jdbc;
    private static final String ROOT = "/api/v1/hr/health";

    @BeforeEach void principals() {
        for (RoleEnum role : List.of(RoleEnum.ADMIN, RoleEnum.MANAGER, RoleEnum.HR_STAFF, RoleEnum.EMPLOYEE)) {
            User user = new User(); user.setId("fixture-" + role.name()); user.setEmail(role.name() + "@example.test");
            user.setFullName("Fixture " + role.name()); user.setRole(role); user.setStatus(UserStatus.ACTIVE);
            String token = role.name(); when(tokens.validateJwtToken(token)).thenReturn(true);
            when(tokens.getEmailFromJwtToken(token)).thenReturn(user.getEmail()); when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
    }
    private static String campaign(String code) {
        return """
                {"code":"%s","name":"Đợt khám giả lập","reportYear":2026,"provider":"Cơ sở khám thử nghiệm",
                 "workforceGroup":"BOTH","startsOn":null,"endsOn":null}
                """.formatted(code);
    }
    private static String indicator(String code, String alias) {
        return """
                {"code":"%s","name":"Chỉ tiêu giả lập","category":"Sinh hóa","valueType":"NUMBER",
                 "status":"ACTIVE","aliases":["%s"]}
                """.formatted(code, alias);
    }
    private String createCampaign(String code) throws Exception {
        mvc.perform(post(ROOT + "/campaigns").header("Authorization", "Bearer ADMIN")
            .contentType("application/json").content(campaign(code))).andExpect(status().isOk());
        return campaigns.findAll().stream().filter(value -> value.getCode().equals(code)).findFirst().orElseThrow().getId();
    }
    @Test void allRoutesRejectGuestsAndHrStaffForAllMethods() throws Exception {
        for (String path : List.of("/capabilities", "/campaigns", "/campaigns/id", "/indicators", "/indicators/id",
                "/subjects/id", "/visits/id", "/results/id", "/imports/id", "/publications/id", "/documents/id/download")) {
            for (var method : List.of(org.springframework.http.HttpMethod.GET, org.springframework.http.HttpMethod.POST,
                    org.springframework.http.HttpMethod.PUT, org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE)) {
                mvc.perform(request(method, ROOT + path)).andExpect(status().isUnauthorized());
                mvc.perform(request(method, ROOT + path).header("Authorization", "Bearer HR_STAFF")).andExpect(status().isForbidden());
            }
        }
    }
    @Test void employeeAndInactiveManagerCannotReadHealth() throws Exception {
        mvc.perform(get(ROOT + "/campaigns").header("Authorization", "Bearer EMPLOYEE")).andExpect(status().isUnauthorized());
        User inactive = new User(); inactive.setId("inactive-fixture"); inactive.setRole(RoleEnum.MANAGER); inactive.setStatus(UserStatus.INACTIVE);
        when(tokens.validateJwtToken("inactive")).thenReturn(true); when(tokens.getEmailFromJwtToken("inactive")).thenReturn("inactive@example.test");
        when(users.findByEmail("inactive@example.test")).thenReturn(Optional.of(inactive));
        mvc.perform(get(ROOT + "/campaigns").header("Authorization", "Bearer inactive")).andExpect(status().isUnauthorized());
    }
    @Test void bothManagementRolesCanListAndCreateWithoutAutoPublishing() throws Exception {
        for (String role : List.of("ADMIN", "MANAGER")) {
            mvc.perform(get(ROOT + "/capabilities").header("Authorization", "Bearer " + role))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
            mvc.perform(post(ROOT + "/campaigns").header("Authorization", "Bearer " + role)
                .contentType("application/json").content(campaign("KSK_" + role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(header().string("Cache-Control", "no-store"));
        }
        assertThat(jdbc.queryForObject("select count(*) from hr_health_publications", Integer.class)).isZero();
        assertThat(audit.count()).isEqualTo(2);
    }
    @Test void updateRequiresVersionAndRejectsStaleRequest() throws Exception {
        String id = createCampaign("EDIT");
        String body = campaign("EDIT").replace("Đợt khám giả lập", "Tên đã sửa");
        mvc.perform(put(ROOT + "/campaigns/" + id).header("Authorization", "Bearer MANAGER").contentType("application/json")
            .content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        body = body.replace("\"startsOn\":null", "\"rowVersion\":0,\"startsOn\":null");
        mvc.perform(put(ROOT + "/campaigns/" + id).header("Authorization", "Bearer MANAGER").contentType("application/json")
            .content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.rowVersion").value(1));
        mvc.perform(put(ROOT + "/campaigns/" + id).header("Authorization", "Bearer MANAGER").contentType("application/json")
            .content(body)).andExpect(status().isConflict());
    }
    @Test void validatesBodyDateRangeAndPaginationAsJson() throws Exception {
        for (String invalid : List.of(campaign("BAD").replace("2026", "1800"), campaign("BAD").replace("BOTH", "UNKNOWN"),
                campaign("BAD").replace("\"startsOn\":null,\"endsOn\":null", "\"startsOn\":\"2026-11-15\",\"endsOn\":\"2026-11-01\""))) {
            mvc.perform(post(ROOT + "/campaigns").header("Authorization", "Bearer ADMIN").contentType("application/json")
                .content(invalid)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        }
        mvc.perform(get(ROOT + "/campaigns?size=101").header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(get(ROOT + "/indicators?page=-1").header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/campaigns?group=UNKNOWN").header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Bộ lọc sổ sức khỏe không hợp lệ."));
    }
    @Test void listsAreFilteredBeforePaginationAndDoNotInterpretWildcards() throws Exception {
        createCampaign("LIST_A"); createCampaign("LIST_B");
        mvc.perform(get(ROOT + "/campaigns?keyword=LIST_&size=1&page=1&year=2026&group=BOTH")
            .header("Authorization", "Bearer MANAGER")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.content[0].code").value("LIST_B"));
        mvc.perform(get(ROOT + "/campaigns?keyword=%25").header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }
    @Test void aliasesAreBatchedVersionedAndProtectedAgainstAmbiguousNames() throws Exception {
        mvc.perform(post(ROOT + "/indicators").header("Authorization", "Bearer ADMIN").contentType("application/json")
            .content(indicator("AST", "AST (máy A)"))).andExpect(status().isOk());
        String id = indicators.findAll().getFirst().getId();
        mvc.perform(get(ROOT + "/indicators").header("Authorization", "Bearer MANAGER"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].aliases[0]").value("AST (máy A)"));
        String update = indicator("AST", "Tên cột mới").replace("\"status\":", "\"rowVersion\":0,\"status\":");
        mvc.perform(put(ROOT + "/indicators/" + id).header("Authorization", "Bearer MANAGER").contentType("application/json")
            .content(update)).andExpect(status().isOk()).andExpect(jsonPath("$.data.rowVersion").value(1));
        mvc.perform(post(ROOT + "/indicators").header("Authorization", "Bearer ADMIN").contentType("application/json")
            .content(indicator("ALT", "Tên cột mới"))).andExpect(status().isConflict());
    }
    @Test void duplicateCodesAndRepeatedAliasAreReportedWithoutSqlLeak() throws Exception {
        createCampaign("DUPE");
        mvc.perform(post(ROOT + "/campaigns").header("Authorization", "Bearer ADMIN").contentType("application/json")
            .content(campaign("dupe"))).andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
        mvc.perform(post(ROOT + "/indicators").header("Authorization", "Bearer ADMIN").contentType("application/json")
            .content(indicator("DUP_ALIAS", "abc").replace("[\"abc\"]", "[\"abc\",\" ABC \"]")))
            .andExpect(status().isBadRequest());
    }
    @Test void generalPersonnelDocumentsCannotRetrieveHealthRecords() throws Exception {
        createCampaign("PRIVATE");
        String id = campaigns.findAll().getFirst().getId();
        for (String path : List.of("/api/v1/hr/employee-documents/" + id + "/download", "/api/v1/hr/employees/" + id + "/documents")) {
            mvc.perform(get(path).header("Authorization", "Bearer HR_STAFF")).andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("select count(*) from hr_audit_events where entity_type like '%HEALTH%'", Integer.class)).isZero();
        assertThat(audit.findAll()).allSatisfy(event -> assertThat(event.getCreatedByActor()).startsWith("USER:"));
    }
    @Test void featureGuardAndServiceAuthorizationCannotBeBypassed() {
        assertThatThrownBy(() -> new HealthAccess(false).requireEnabled()).hasMessage("Sổ sức khỏe chưa được bật.");
        assertThatThrownBy(() -> campaignService.create(new HrHealthDtos.CampaignRequest("NO", "No", 2026, "Test", null, null,
            com.booking.system.health.entity.HealthTypes.WorkforceGroup.BOTH, null), new HrImportActor("USER:9", "Test", "HR_STAFF")))
            .isInstanceOf(com.booking.system.hr.api.HrApiException.class);
    }
    @Test void foundationHasConstraintsNoSeedAndIndexedCampaignReads() throws Exception {
        createCampaign("INDEXED");
        String plan = jdbc.queryForObject("EXPLAIN SELECT * FROM hr_health_campaigns WHERE report_year = 2026 ORDER BY code, id LIMIT 20", String.class);
        assertThat(plan).contains("idx_health_campaign_year_code");
        System.out.println("HEALTH_H2_EXPLAIN=" + plan.replace('\n', ' '));
        assertThat(jdbc.queryForObject("select count(*) from hr_health_subjects", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from hr_health_results", Integer.class)).isZero();
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = {"com.booking.system.health.entity", "com.booking.system.hr.entity"})
    @EnableJpaRepositories(basePackages = {"com.booking.system.health.repository", "com.booking.system.hr.repository"})
    @Import({SecurityConfig.class, HealthController.class, HealthExceptionHandler.class, HrActorResolver.class,
        com.booking.system.hr.api.HrEmployeeDocumentController.class, com.booking.system.hr.service.HrEmployeeDocumentService.class,
        com.booking.system.hr.importer.HrImportJsonCodec.class, DocumentErrors.class,
        HealthCampaignService.class, HealthIndicatorService.class, HealthAuditService.class, HealthAccess.class, HealthConfiguration.class})
    static class Config {
        @Bean JwtUtils jwtUtils() { return mock(JwtUtils.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean JwtAuthFilter jwtAuthFilter(JwtUtils jwt, UserRepository users) { return new JwtAuthFilter(jwt, users); }
    }
    @org.springframework.web.bind.annotation.RestControllerAdvice(assignableTypes = com.booking.system.hr.api.HrEmployeeDocumentController.class)
    static class DocumentErrors extends com.booking.system.hr.api.HrApiExceptionHandler { }
}
