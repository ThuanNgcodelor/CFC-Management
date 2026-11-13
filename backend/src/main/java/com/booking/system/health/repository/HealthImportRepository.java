package com.booking.system.health.repository;
import com.booking.system.health.entity.HealthImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
public interface HealthImportRepository extends JpaRepository<HealthImport,String> {
    Optional<HealthImport> findByCampaignIdAndSourceHash(String campaignId, String hash);
    Page<HealthImport> findByCampaignIdOrderByCreatedAtDesc(String campaignId, Pageable pageable);
}
