package com.booking.system.health.repository;
import com.booking.system.health.entity.HealthMappingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface HealthMappingRepository extends JpaRepository<HealthMappingProfile,String> {
    Page<HealthMappingProfile> findByFingerprintOrderByName(String fingerprint, Pageable pageable);
}
