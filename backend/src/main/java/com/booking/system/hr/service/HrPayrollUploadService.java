package com.booking.system.hr.service;

import com.booking.system.hr.api.dto.HrPayrollDtos;
import com.booking.system.hr.enums.HrPayrollType;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Parsing and workbook I/O must complete before the persistence transaction opens. */
@Service
@RequiredArgsConstructor
public class HrPayrollUploadService {
    private final HrPayrollWorkbookParser standard;
    private final HrLeadershipPayrollParser leadership;
    private final HrPayrollService persistence;
    public HrPayrollDtos.ImportResponse upload(String name, byte[] bytes, HrPayrollType type, HrImportActor actor) {
        var parsed = type == HrPayrollType.LEADERSHIP ? leadership.parse(bytes) : standard.parse(bytes);
        return persistence.persist(name, parsed, type, actor);
    }
}
