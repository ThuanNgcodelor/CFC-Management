package com.booking.system.health.repository;

import com.booking.system.health.entity.HealthCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HealthCampaignRepository extends JpaRepository<HealthCampaign, String>, JpaSpecificationExecutor<HealthCampaign> {
    boolean existsByCode(String code);
    java.util.List<com.booking.system.health.entity.HealthCampaign> findByCodeIn(java.util.Collection<String> codes);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from HealthCampaign c where c.id=:id")
    java.util.Optional<HealthCampaign> lockById(String id);
}
