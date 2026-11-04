package com.booking.system.hr;

import com.booking.system.entity.User;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrMovementDtos;
import com.booking.system.hr.entity.*;
import com.booking.system.hr.enums.*;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.service.HrMovementSearchQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:hr_movement_search;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider=org.hibernate.tool.schema.internal.DefaultSchemaFilterProvider"
})
@ContextConfiguration(classes = HrMovementSearchRepositoryTest.TestApplication.class)
class HrMovementSearchRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired HrEmployeeMovementRepository repository;
    private HrDepartment department;
    private List<HrEmployeeMovement> movements;

    @BeforeEach
    void setUp() {
        department = new HrDepartment();
        department.setCode("DEMO"); department.setName("Đơn vị giả lập");
        audit(department); entityManager.persist(department);
        movements = new ArrayList<>();
        User user = new User();
        user.setEmail("fixture@example.test"); user.setFullName("A Người xử lý");
        entityManager.persist(user);
        for (int i = 0; i < 25; i++) {
            HrEmployee employee = new HrEmployee();
            employee.setEmployeeCode(String.format("DEMO%02d", i));
            employee.setFullName("Nhân sự giả lập " + i);
            employee.setWorkforceGroup(i < 20 ? HrWorkforceGroup.GENERAL_LABOR : HrWorkforceGroup.OFFICE);
            audit(employee); entityManager.persist(employee);
            HrEmployeeMovement movement = new HrEmployeeMovement();
            movement.setEmployee(employee);
            movement.setMovementType(i % 2 == 0 ? HrMovementType.INCREASE : HrMovementType.DECREASE);
            movement.setStatus(i % 2 == 0 ? HrMovementStatus.DRAFT : HrMovementStatus.CONFIRMED);
            movement.setEffectiveDate(LocalDate.of(2026, 9, i + 1));
            movement.setDecisionNumber(i == 0 ? "50_%/HĐLĐ-PBHC/2026" : i + "/QĐ/2026");
            movement.setSourceKind(HrMovementSourceKind.MANUAL);
            movement.setToDepartment(department);
            audit(movement);
            movement.setCreatedByActor(i == 0 ? "USER:" + user.getId() : "Z Người xử lý");
            if (i % 2 == 1) movement.setConfirmedAt(LocalDateTime.of(2026, 10, 1, 12, i));
            entityManager.persist(movement);
            movements.add(movement);
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void movementPageDoesNotLoadEmployeeSectionsOncePerRow() {
        var factory = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class);
        var statistics = factory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var page = search(null, null, null, null, null, null, null, "ALL", 0, 20, "employeeCode,desc");
            page.map(com.booking.system.hr.api.dto.HrMovementResponse::from).getContent();
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void searchesAndSortsBeforePaginationWithStableTieBreaks() {
        Page<HrEmployeeMovement> first = search(null, null, null, null, null, null, null, "ALL", 0, 20, "employeeCode,desc");
        Page<HrEmployeeMovement> second = search(null, null, null, null, null, null, null, "ALL", 1, 20, "employeeCode,desc");
        assertThat(first.getTotalElements()).isEqualTo(25);
        assertThat(first.getContent().getFirst().getEmployee().getEmployeeCode()).isEqualTo("DEMO24");
        assertThat(first.getContent().getLast().getEmployee().getEmployeeCode()).isEqualTo("DEMO05");
        assertThat(second.getContent()).hasSize(5);
        assertThat(second.getContent().getFirst().getEmployee().getEmployeeCode()).isEqualTo("DEMO04");
        assertThat(first.stream().map(HrEmployeeMovement::getId)).doesNotContainAnyElementsOf(second.map(HrEmployeeMovement::getId).getContent());
    }

    @Test
    void combinesDatesGroupStatusAndDepartmentAndEscapesWildcardInput() {
        Page<HrEmployeeMovement> result = search(null, HrMovementType.DECREASE, HrMovementStatus.CONFIRMED,
                HrWorkforceGroup.GENERAL_LABOR, department.getId(), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 10), "ALL", 0, 50, "effectiveDate,asc");
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.stream().map(HrEmployeeMovement::getEffectiveDate)).containsExactly(
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10));
        assertThat(search("50_%", null, null, null, null, null, null, "ALL", 0, 20, "effectiveDate,asc").getTotalElements()).isEqualTo(1);
        assertThat(search("DEMO23", null, null, null, null, null, null, "ALL", 0, 20, "effectiveDate,asc").getTotalElements()).isEqualTo(1);
    }

    @Test
    void allSupportedSortsExecuteAndActorSortUsesResolvedName() {
        for (String sort : List.of("employeeName", "movementType", "status", "effectiveDate", "createdAt", "processedAt", "processedBy")) {
            assertThat(search(null, null, null, null, null, null, null, "ALL", 0, 50, sort + ",asc").getTotalElements()).isEqualTo(25);
        }
        assertThat(search(null, null, null, null, null, null, null, "ALL", 0, 50, "processedBy,asc")
                .getContent().getFirst().getEmployee().getEmployeeCode()).isEqualTo("DEMO00");
    }

    @Test
    void distinguishesCorrectionAndSupersededRecordsWithoutHidingHistory() {
        HrEmployeeMovement original = entityManager.find(HrEmployeeMovement.class, movements.get(0).getId());
        HrEmployeeMovement correction = entityManager.find(HrEmployeeMovement.class, movements.get(1).getId());
        correction.setCorrectionOfMovement(original);
        entityManager.flush();
        assertThat(search(null, null, null, null, null, null, null, "ADJUSTMENT", 0, 20, "effectiveDate,asc").getContent())
                .extracting(HrEmployeeMovement::getId).containsExactly(correction.getId());
        assertThat(search(null, null, null, null, null, null, null, "SUPERSEDED", 0, 20, "effectiveDate,asc").getContent())
                .extracting(HrEmployeeMovement::getId).containsExactly(original.getId());
        assertThat(search(null, null, null, null, null, null, null, "ORIGINAL", 0, 50, "effectiveDate,asc").getTotalElements()).isEqualTo(24);
    }

    @Test
    void rejectsUnsupportedSortAndDateRangeAndCapsPageSize() {
        assertThatThrownBy(() -> HrMovementSearchQuery.pageable(0, 20, "employee.password,asc")).isInstanceOf(HrApiException.class);
        assertThatThrownBy(() -> HrMovementSearchQuery.validate(new HrMovementDtos.SearchRequest(
                null, null, null, null, null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), null)))
                .isInstanceOf(HrApiException.class);
        assertThat(HrMovementSearchQuery.pageable(-2, 5000, "status,asc").getPageSize()).isEqualTo(50);
    }

    private Page<HrEmployeeMovement> search(String keyword, HrMovementType type, HrMovementStatus status,
            HrWorkforceGroup group, String unit, LocalDate from, LocalDate to, String recordType, int page, int size, String sort) {
        return repository.searchActivity(HrMovementSearchQuery.keyword(keyword), type, status, group, unit, from, to,
                recordType, HrMovementSearchQuery.pageable(page, size, sort));
    }

    private static void audit(HrAuditable entity) {
        entity.setCreatedByActor("FIXTURE"); entity.setUpdatedByActor("FIXTURE");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = {"com.booking.system.hr.entity", "com.booking.system.entity"})
    @EnableJpaRepositories(basePackages = "com.booking.system.hr.repository")
    static class TestApplication { }
}
