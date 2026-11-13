package com.booking.system.health.repository;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.api.HrHealthRecordDtos.Person;
import com.booking.system.health.entity.*;
import jakarta.persistence.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class HealthPublicationReadRepository {
    @PersistenceContext private EntityManager em;
    private static final String INFO="select new com.booking.system.health.api.HrHealthPublicationDtos$PublicationInfo(p.id,p.visitId,p.revision,p.status,p.rowVersion,p.contentHash,p.publishReason,p.revokeReason,p.createdAt,p.createdByActor) from HealthPublication p ";
    private static final String LATEST="not exists(select x.id from HealthPublication x where x.visitId=p.visitId and x.revision>p.revision)";
    public Page<Person> people(Scope scope,String pattern,Pageable page) {
        String from=" from HrEmployee e join HealthSubject s on s.employeeId=e.id join HealthVisit v on v.subjectId=s.id join HealthCampaign c on c.id=v.campaignId where c.id=:campaign and (lower(e.employeeCode) like :pattern escape '!' or lower(e.fullName) like :pattern escape '!') and (:group is null or e.workforceGroup=:group)";
        var args=new HashMap<String,Object>();args.put("campaign",scope.campaignId());args.put("pattern",pattern);args.put("group",scope.group());
        if(scope.employeeIds()!=null){if(scope.employeeIds().isEmpty())return Page.empty(page);from+=" and e.id in :people";args.put("people",scope.employeeIds());}
        String fields="e.id,e.employeeCode,e.fullName,e.dateOfBirth,e.workforceGroup";
        var values=query("select new com.booking.system.health.api.HrHealthRecordDtos$Person("+fields+",count(v.id),max(c.reportYear))"+from+" group by "+fields+" order by e.employeeCode,e.id",Person.class,args).setFirstResult(Math.toIntExact(page.getOffset())).setMaxResults(page.getPageSize()).getResultList();
        long count=query("select count(distinct e.id)"+from,Long.class,args).getSingleResult();return new PageImpl<>(values,page,count);
    }
    public List<HealthVisit> visits(String campaign,Collection<String> employeeIds) {
        if(employeeIds.isEmpty())return List.of();
        return query("select v from HealthVisit v join HealthSubject s on s.id=v.subjectId where v.campaignId=:campaign and s.employeeId in :people order by s.employeeId,v.id",HealthVisit.class,Map.of("campaign",campaign,"people",employeeIds)).setMaxResults(501).getResultList();
    }
    public List<PublicationInfo> latest(Collection<String> visits) {
        if(visits.isEmpty())return List.of();return query(INFO+"where p.visitId in :visits and "+LATEST,PublicationInfo.class,Map.of("visits",visits)).getResultList();
    }
    public Page<PublicationInfo> history(String employee,String visit,Pageable page) {
        String where="where p.visitId=:visit and exists(select v.id from HealthVisit v join HealthSubject s on s.id=v.subjectId where v.id=p.visitId and s.employeeId=:employee)";
        var args=Map.<String,Object>of("visit",visit,"employee",employee);
        var rows=query(INFO+where+" order by p.revision desc",PublicationInfo.class,args).setFirstResult(Math.toIntExact(page.getOffset())).setMaxResults(page.getPageSize()).getResultList();
        long count=query("select count(p.id) from HealthPublication p "+where,Long.class,args).getSingleResult();return new PageImpl<>(rows,page,count);
    }
    public boolean isLatestPublished(String id) {return query("select count(p.id) from HealthPublication p where p.id=:id and p.status='PUBLISHED' and "+LATEST,Long.class,Map.of("id",id)).getSingleResult()==1;}
    public List<HealthVisit> lockVisits(Collection<String> ids) {return query("select v from HealthVisit v where v.id in :ids order by v.id",HealthVisit.class,Map.of("ids",ids)).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();}
    public void lockIndicators(Collection<String> ids){if(!ids.isEmpty())query("select i from HealthIndicator i where i.id in :ids order by i.id",HealthIndicator.class,Map.of("ids",ids)).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();}
    private <T> TypedQuery<T> query(String hql,Class<T> type,Map<String,Object> args){var q=em.createQuery(hql,type);args.forEach(q::setParameter);return q;}
}
