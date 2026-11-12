package com.booking.system.health.repository;
import com.booking.system.hr.entity.HrEmployee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;
public interface HealthEmployeeRepository extends JpaRepository<HrEmployee,String> {
    @Query("select e from HrEmployee e where upper(trim(e.employeeCode)) in :codes or lower(trim(e.fullName)) in :names order by e.id")
    List<HrEmployee> candidates(Collection<String> codes, Collection<String> names, Pageable pageable);
    @Query("select e from HrEmployee e where lower(e.employeeCode) like :pattern escape '!' or lower(e.fullName) like :pattern escape '!' order by e.employeeCode, e.id")
    List<HrEmployee> search(String pattern, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from HrEmployee e where e.id in :ids order by e.id")
    List<HrEmployee> lockEmployees(Collection<String> ids);
}
