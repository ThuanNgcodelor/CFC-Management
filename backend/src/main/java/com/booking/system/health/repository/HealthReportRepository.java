package com.booking.system.health.repository;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.api.HrHealthReportDtos.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Bounded projections for a single consistent report snapshot; no managed entity graph. */
@Repository
public class HealthReportRepository {
    @PersistenceContext private EntityManager em;
    private static final String DTO="com.booking.system.health.api.HrHealthRecordDtos$";
    private static final String VISITS=" from HealthVisit v join HealthSubject s on s.id=v.subjectId join HealthCampaign c on c.id=v.campaignId ";
    private static final String COUNT="(select count(v.id)"+VISITS+"where s.employeeId=e.id and c.reportYear between :from and :to)";
    private static final String LAST="(select max(c.reportYear)"+VISITS+"where s.employeeId=e.id and c.reportYear between :from and :to)";
    private static final Map<String,String> SORT=Map.of("codeAsc","e.employeeCode asc","codeDesc","e.employeeCode desc","nameAsc","e.fullName asc","nameDesc","e.fullName desc");

    public List<Person> people(Request request,String pattern,int limit) {
        String where=" where (:employee is null or e.id=:employee) and (lower(e.employeeCode) like :pattern escape '!' or lower(e.fullName) like :pattern escape '!') and (:group is null or e.workforceGroup=:group) ";
        where+=switch(request.data()){case "WITH_DATA"->"and "+COUNT+">0 ";case "WITHOUT_DATA"->"and "+COUNT+"=0 ";default->"";};
        var args=new HashMap<String,Object>();args.put("employee",request.employeeId());args.put("pattern",pattern);args.put("group",request.group());args.put("from",request.fromYear());args.put("to",request.toYear());
        String hql="select new "+DTO+"Person(e.id,e.employeeCode,e.fullName,e.dateOfBirth,e.workforceGroup,"+COUNT+","+LAST+") from HrEmployee e"+where+"order by "+SORT.get(request.sort())+",e.id";
        return query(hql,Person.class,args,limit);
    }
    public List<VisitRow> visits(Collection<String> people,Request request,int limit) {
        if(people.isEmpty())return List.of();
        String fields="v.id,c.reportYear,c.name,c.provider,v.examinedOn,v.status,v.sourceNamespace,v.sourceSid,(select count(r.id) from HealthResult r where r.visitId=v.id)";
        // Nested DTO construction is avoided for portability across H2 and MySQL/Hibernate.
        String hql="select s.employeeId,"+fields+VISITS+"where s.employeeId in :people and c.reportYear between :from and :to";
        var args=new HashMap<String,Object>();args.put("people",people);args.put("from",request.fromYear());args.put("to",request.toYear());
        if(request.visitIds()!=null&&!request.visitIds().isEmpty()){hql+=" and v.id in :visits";args.put("visits",request.visitIds());}
        return query(hql+" order by s.employeeId,c.reportYear,v.examinedOn nulls last,v.id",Object[].class,args,limit).stream()
            .map(v->new VisitRow((String)v[0],new Visit((String)v[1],(Integer)v[2],(String)v[3],(String)v[4],(java.time.LocalDate)v[5],(String)v[6],(String)v[7],(String)v[8],(Long)v[9]))).toList();
    }
    public List<Measurement> results(Collection<String> visits,int limit) {
        if(visits.isEmpty())return List.of();
        String hql="select new "+DTO+"Measurement(r.visitId,r.valueKey,i.id,i.code,i.name,i.category,i.valueType,r.rawValue,r.unit,r.method,r.referenceText,r.sourceSheet,r.sourceRow,r.sourceColumn,r.sourceLabel) from HealthResult r join HealthIndicator i on i.id=r.indicatorId where r.visitId in :visits order by r.visitId,i.category,i.name,r.valueKey";
        return query(hql,Measurement.class,Map.of("visits",visits),limit);
    }
    private <T> List<T> query(String hql,Class<T> type,Map<String,Object> args,int limit) {
        var query=em.createQuery(hql,type);args.forEach(query::setParameter);return query.setMaxResults(limit).getResultList();
    }
}
