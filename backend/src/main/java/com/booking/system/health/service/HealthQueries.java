package com.booking.system.health.service;

import com.booking.system.hr.api.HrApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.Locale;

final class HealthQueries {
    static final int MAX_SIZE = 100;
    private HealthQueries() { }
    static PageRequest page(int page, int size, boolean campaigns) {
        if (page < 0 || page > 10000 || size < 1 || size > MAX_SIZE) {
            throw HrApiException.badRequest("HEALTH_PAGE_INVALID", "Trang không hợp lệ; số dòng mỗi trang phải từ 1 đến 100.");
        }
        Sort sort = campaigns ? Sort.by(Sort.Order.desc("reportYear"), Sort.Order.asc("code"), Sort.Order.asc("id"))
                : Sort.by("code", "id");
        return PageRequest.of(page, size, sort);
    }
    static <T> Specification<T> keyword(String value) {
        if (value == null || value.isBlank()) return (root, query, cb) -> cb.conjunction();
        if (value.length() > 100) throw HrApiException.badRequest("HEALTH_SEARCH_INVALID", "Từ khóa tối đa 100 ký tự.");
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("code")), pattern, '!'), cb.like(cb.lower(root.get("name")), pattern, '!'));
    }
    static String code(String value) { return value.trim().toUpperCase(Locale.ROOT); }
}
