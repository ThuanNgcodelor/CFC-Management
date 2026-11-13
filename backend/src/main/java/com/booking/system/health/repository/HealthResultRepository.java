package com.booking.system.health.repository;
import com.booking.system.health.entity.HealthResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
public interface HealthResultRepository extends JpaRepository<HealthResult,String> {
    List<HealthResult> findByVisitIdIn(Collection<String> visitIds);
}
