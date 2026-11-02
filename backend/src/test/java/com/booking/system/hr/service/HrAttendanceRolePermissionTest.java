package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrProductionAttendanceDtos;
import com.booking.system.hr.entity.HrProductionAttendanceImport;
import com.booking.system.hr.enums.HrAttendanceImportStatus;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrProductionAttendanceImportRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HrAttendanceRolePermissionTest {
    @Mock private HrProductionAttendanceImportRepository importRepository;
    @Mock private HrNightRewardService nightRewardService;
    @Mock private HrAuditEventRepository auditRepository;
    @Mock private EntityManager entityManager;
    @InjectMocks private HrProductionAttendanceService service;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "HR_STAFF"})
    void authorizedAttendanceRolesCanReopenAndInvalidateNightRewards(String role) {
        HrProductionAttendanceImport batch = new HrProductionAttendanceImport();
        batch.setId("import-1");
        batch.setAttendanceMonth("2026-08");
        batch.setStatus(HrAttendanceImportStatus.CONFIRMED);
        when(importRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
        HrImportActor actor = new HrImportActor("USER:staff", "Người phụ trách", role);

        var response = service.reopenImport(batch.getId(),
                new HrProductionAttendanceDtos.ReopenImportRequest("Đối soát lại", batch.getRowVersion()), actor);

        assertThat(response.status()).isEqualTo(HrAttendanceImportStatus.PREVIEWED);
        assertThat(batch.getConfirmedAt()).isNull();
        verify(importRepository).save(batch);
        verify(nightRewardService).markStaleForAttendanceImport(eq(batch.getId()), eq(actor), contains("Đối soát lại"));
        verify(auditRepository).save(argThat(event -> "USER:staff".equals(event.getActorSubject())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "SYSTEM"})
    void otherActorsCannotReopenAttendance(String role) {
        assertThatThrownBy(() -> service.reopenImport("import-1",
                new HrProductionAttendanceDtos.ReopenImportRequest("Đối soát lại", 0),
                new HrImportActor("USER:other", "Người dùng", role)))
                .isInstanceOf(HrApiException.class);
        verifyNoInteractions(importRepository, nightRewardService, auditRepository);
    }
}
