package com.booking.system.health.repository;
import com.booking.system.health.entity.HealthSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
public interface HealthSubjectRepository extends JpaRepository<HealthSubject,String> {
    List<HealthSubject> findByEmployeeIdIn(Collection<String> employeeIds);
}
