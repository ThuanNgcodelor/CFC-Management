package com.booking.system.health.repository;

import com.booking.system.health.entity.HealthIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HealthIndicatorRepository extends JpaRepository<HealthIndicator, String>, JpaSpecificationExecutor<HealthIndicator> {
    boolean existsByCode(String code);
    java.util.List<HealthIndicator> findByCodeIn(java.util.Collection<String> codes);
    @org.springframework.data.jpa.repository.Query("select i from HealthIndicator i where lower(i.name) in :names or lower(i.code) in :names")
    java.util.List<HealthIndicator> matchNames(java.util.Collection<String> names, org.springframework.data.domain.Pageable pageable);
}
