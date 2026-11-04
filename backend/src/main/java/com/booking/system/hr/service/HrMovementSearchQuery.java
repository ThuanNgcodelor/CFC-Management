package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrMovementDtos;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HrMovementSearchQuery {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String ACTOR = "coalesce(movement.confirmedByActor, movement.cancelledByActor, movement.createdByActor)";
    private static final Map<String, String> SORTS = Map.of(
            "employeeCode", "employee.employeeCode",
            "employeeName", "employee.fullName",
            "movementType", "movementType",
            "effectiveDate", "effectiveDate",
            "status", "status",
            "createdAt", "createdAt",
            "processedAt", "coalesce(movement.confirmedAt, movement.cancelledAt, movement.createdAt)",
            "processedBy", "lower(case when " + ACTOR + " like 'SYSTEM:%' then 'Hệ thống' when " + ACTOR
                    + " like 'USER:%' then coalesce((select coalesce(nullif(trim(processor.fullName), ''), nullif(trim(processor.email), ''), 'Người quản lý')"
                    + " from com.booking.system.entity.User processor where processor.id = substring(" + ACTOR + ", 6)), 'Người quản lý') else " + ACTOR + " end)"
    );
    private static final Set<String> RECORD_TYPES = Set.of("ALL", "ORIGINAL", "ADJUSTMENT", "SUPERSEDED");

    private HrMovementSearchQuery() { }

    public static void validate(HrMovementDtos.SearchRequest request) {
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw HrApiException.badRequest("MOVEMENT_DATE_RANGE_INVALID", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        }
        recordType(request.recordType());
    }

    public static String keyword(String value) {
        String normalized = text(value);
        if (normalized == null) return null;
        return "%" + normalized.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    public static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String recordType(String value) {
        String result = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if (!RECORD_TYPES.contains(result)) {
            throw HrApiException.badRequest("MOVEMENT_RECORD_TYPE_INVALID", "Loại bản ghi biến động không hợp lệ.");
        }
        return result;
    }

    public static PageRequest pageable(int page, int size, String value) {
        String[] parts = (value == null || value.isBlank() ? "effectiveDate,desc" : value).split(",", -1);
        String property = SORTS.get(parts[0]);
        if (property == null || parts.length != 2 || !Set.of("asc", "desc").contains(parts[1])) {
            throw HrApiException.badRequest("MOVEMENT_SORT_INVALID", "Cách sắp xếp biến động không hợp lệ.");
        }
        Sort.Direction direction = "asc".equals(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = property.contains("(") ? JpaSort.unsafe(direction, property) : Sort.by(direction, property);
        sort = sort.and(Sort.by(direction, "id"));
        return PageRequest.of(Math.max(0, page), size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE), sort);
    }
}
