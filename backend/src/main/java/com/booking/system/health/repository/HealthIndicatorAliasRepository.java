package com.booking.system.health.repository;

import com.booking.system.health.entity.HealthIndicatorAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface HealthIndicatorAliasRepository extends JpaRepository<HealthIndicatorAlias, String> {
    List<HealthIndicatorAlias> findByNormalizedNameIn(Collection<String> names);
    List<HealthIndicatorAlias> findByIndicatorIdIn(Collection<String> ids);
    boolean existsByNormalizedNameInAndIndicatorIdNot(Collection<String> names, String indicatorId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from HealthIndicatorAlias a where a.indicatorId = :id")
    void deleteByIndicatorId(String id);
}
