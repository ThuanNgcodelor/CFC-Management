package com.booking.system.hr.repository;

import com.booking.system.hr.entity.HrProductionAttendanceImport;
import com.booking.system.hr.enums.HrAttendanceImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HrProductionAttendanceImportRepository extends HrRepository<HrProductionAttendanceImport, String> {
    Optional<HrProductionAttendanceImport> findByFileSha256(String fileSha256);
    Page<HrProductionAttendanceImport> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<HrProductionAttendanceImport> findByAttendanceMonthOrderByCreatedAtDesc(String month, Pageable pageable);
    List<HrProductionAttendanceImport> findByAttendanceMonthAndStatusOrderByCreatedAtAsc(
            String month, HrAttendanceImportStatus status);
    List<HrProductionAttendanceImport> findByAttendanceMonthAndStatusIn(
            String month, java.util.Collection<HrAttendanceImportStatus> statuses);
    @Query("select distinct batch.attendanceMonth from HrProductionAttendanceImport batch where batch.attendanceMonth between :from and :through and batch.status = :status")
    List<String> findConfirmedMonthsInRange(@Param("from") String from, @Param("through") String through,
                                            @Param("status") HrAttendanceImportStatus status);
    void deleteById(String id);
}
