package com.booking.system.health.config;

import com.booking.system.health.importer.history.HealthHistoryImport;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
public class HealthHistoryStartup {
    private final HealthHistoryImport history;
    @Value("${cfc.health.enabled:true}") private boolean enabled;
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        if(enabled)history.start(new HrImportActor("SYSTEM:health-history","Nhập lịch sử khi khởi động","ADMIN"),false,"Tự nhập gói dữ liệu lịch sử sau triển khai.");
    }
}
