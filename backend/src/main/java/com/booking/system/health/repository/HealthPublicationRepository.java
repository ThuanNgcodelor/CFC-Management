package com.booking.system.health.repository;

import com.booking.system.health.entity.HealthPublication;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HealthPublicationRepository extends JpaRepository<HealthPublication,String> {
    String OWNED_CURRENT = " from HealthPublication p join HealthVisit v on v.id=p.visitId join HealthSubject s on s.id=v.subjectId join HealthCampaign c on c.id=v.campaignId where s.employeeId=:employee and p.status='PUBLISHED' and not exists(select x.id from HealthPublication x where x.visitId=p.visitId and x.revision>p.revision)";
    @org.springframework.data.jpa.repository.Query("select p" + OWNED_CURRENT + " and p.id=:id")
    java.util.Optional<HealthPublication> ownedCurrent(String employee, String id);
    @org.springframework.data.jpa.repository.Query(value="select p" + OWNED_CURRENT + " and p.reportYear between :from and :to order by p.reportYear desc,p.createdAt desc,p.id",
        countQuery="select count(p.id)" + OWNED_CURRENT + " and p.reportYear between :from and :to")
    org.springframework.data.domain.Page<HealthPublication> currentPage(String employee, int from, int to, org.springframework.data.domain.Pageable page);
}
