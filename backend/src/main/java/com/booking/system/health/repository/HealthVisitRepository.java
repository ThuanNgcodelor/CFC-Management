package com.booking.system.health.repository;
import com.booking.system.health.entity.HealthVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
public interface HealthVisitRepository extends JpaRepository<HealthVisit,String> {
    List<HealthVisit> findBySourceNamespaceAndSourceSidIn(String namespace, Collection<String> sids);
}
