package com.booking.system.health.repository;
import com.booking.system.health.entity.HealthImportRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
public interface HealthImportRowRepository extends JpaRepository<HealthImportRow,String> {
    interface Counts { String getBatchId(); String getState(); long getTotal(); }
    @Query("select r.batchId as batchId, r.state as state, count(r) as total from HealthImportRow r where r.batchId in :ids group by r.batchId,r.state")
    List<Counts> countStates(Collection<String> ids);
    Page<HealthImportRow> findByBatchIdOrderBySourceRow(String batchId, Pageable pageable);
    Page<HealthImportRow> findByBatchIdAndStateOrderBySourceRow(String batchId,String state,Pageable pageable);
    List<HealthImportRow> findByBatchIdAndIdIn(String batchId, Collection<String> ids);
    long countByBatchIdAndState(String batchId, String state);
    @Modifying @Query("update HealthImportRow r set r.state='CANCELLED', r.payload=null, r.rowVersion=r.rowVersion+1 where r.batchId=:batchId and r.state='PENDING'")
    int cancelPending(String batchId);
}
