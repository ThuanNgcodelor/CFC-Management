package com.booking.system.health.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.entity.HrAuditable;
import com.booking.system.hr.importer.HrImportActor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HealthAccess {
    private final boolean enabled;
    public HealthAccess(@Value("${cfc.health.enabled:false}") boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public void requireEnabled() {
        if (!enabled) throw HrApiException.notFound("HEALTH_DISABLED", "Sổ sức khỏe chưa được bật.");
    }
    public void requireManager(HrImportActor actor) {
        if (actor == null || !("ADMIN".equals(actor.role()) || "MANAGER".equals(actor.role()))) {
            throw HrApiException.forbidden("HEALTH_ACCESS_DENIED", "Chỉ quản trị viên và quản trị nhân sự được quản lý sổ sức khỏe.");
        }
        requireEnabled();
    }
    public static void checkVersion(HrAuditable entity, Long version) {
        if (version == null) throw HrApiException.badRequest("HEALTH_VERSION_REQUIRED", "Thiếu phiên bản dữ liệu. Vui lòng tải lại.");
        if (entity.getRowVersion() != version) throw HrApiException.conflict("HEALTH_VERSION_CONFLICT", "Dữ liệu đã thay đổi. Vui lòng tải lại trước khi lưu.");
    }
    public static void audit(HrAuditable entity, HrImportActor actor, boolean creating) {
        if (creating) entity.setCreatedByActor(actor.subject());
        entity.setUpdatedByActor(actor.subject());
        if (!creating) entity.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
    }
}
