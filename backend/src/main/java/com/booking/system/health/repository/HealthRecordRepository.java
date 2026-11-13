package com.booking.system.health.repository;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.hr.enums.HrWorkforceGroup;
import jakarta.persistence.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Projections keep private health reads independent of the general HR DTO/document feed. */
@Repository
public class HealthRecordRepository {
    @PersistenceContext private EntityManager em;
    private static final String DTO="com.booking.system.health.api.HrHealthRecordDtos$";
    private static final String VISITS=" from HealthVisit v join HealthSubject s on s.id=v.subjectId join HealthCampaign c on c.id=v.campaignId ";
    private static final String COUNT="(select count(v.id)"+VISITS+"where s.employeeId=e.id and (:year is null or c.reportYear=:year))";
    private static final String LAST="(select max(c.reportYear)"+VISITS+"where s.employeeId=e.id and (:year is null or c.reportYear=:year))";
    private static final String PERSON="select new "+DTO+"Person(e.id,e.employeeCode,e.fullName,e.dateOfBirth,e.workforceGroup,"+COUNT+","+LAST+") from HrEmployee e ";
    private static final String VISIT="select new "+DTO+"Visit(v.id,c.reportYear,c.name,c.provider,v.examinedOn,v.status,v.sourceNamespace,v.sourceSid,(select count(r.id) from HealthResult r where r.visitId=v.id))";
    private static final String MEASUREMENT="select new "+DTO+"Measurement(r.visitId,r.valueKey,i.id,i.code,i.name,i.category,i.valueType,r.rawValue,r.unit,r.method,r.referenceText,r.sourceSheet,r.sourceRow,r.sourceColumn,r.sourceLabel)";
    private static final String RESULT_FROM=" from HealthResult r join HealthIndicator i on i.id=r.indicatorId ";
    private static final String RESULT_MATCH=" and (lower(i.name) like :pattern escape '!' or lower(i.code) like :pattern escape '!') ";

    public Page<Person> people(String pattern,Integer year,HrWorkforceGroup group,String data,String order,Pageable page) {
        String where="where (lower(e.employeeCode) like :pattern escape '!' or lower(e.fullName) like :pattern escape '!') and (:group is null or e.workforceGroup=:group) ";
        where+=switch(data){case "WITH_DATA" -> "and "+COUNT+">0 ";case "WITHOUT_DATA" -> "and "+COUNT+"=0 ";default -> "";};
        Map<String,Object> args=new HashMap<>();args.put("pattern",pattern);args.put("group",group);args.put("year",year);
        return page(PERSON+where+"order by "+order+",e.id","select count(e.id) from HrEmployee e "+where,Person.class,args,page);
    }
    public Optional<Person> person(String id) {
        return query(PERSON+"where e.id=:id",Person.class,nullableArgs("id",id,"year",null)).getResultStream().findFirst();
    }
    public Page<Visit> timeline(String employeeId,int from,int to,Pageable page) {
        String where="where s.employeeId=:id and c.reportYear between :from and :to ";
        return page(VISIT+VISITS+where+"order by c.reportYear desc,v.examinedOn desc nulls last,v.id", "select count(v.id)"+VISITS+where,Visit.class,Map.of("id",employeeId,"from",from,"to",to),page);
    }
    public List<YearCount> years(String employeeId,int from,int to) {
        return query("select new "+DTO+"YearCount(c.reportYear,count(v.id))"+VISITS+"where s.employeeId=:id and c.reportYear between :from and :to group by c.reportYear",YearCount.class,Map.of("id",employeeId,"from",from,"to",to)).getResultList();
    }
    public List<Visit> ownedVisits(String employeeId,Collection<String> ids) {
        return query(VISIT+VISITS+"where s.employeeId=:id and v.id in :ids order by c.reportYear,v.examinedOn nulls last,v.id",Visit.class,Map.of("id",employeeId,"ids",ids)).getResultList();
    }
    public Page<Measurement> results(String visitId,String pattern,Pageable page) {
        String where="where r.visitId=:id"+RESULT_MATCH;
        return page(MEASUREMENT+RESULT_FROM+where+"order by i.category,i.name,r.valueKey", "select count(r.id)"+RESULT_FROM+where,Measurement.class,Map.of("id",visitId,"pattern",pattern),page);
    }
    public Page<IndicatorKey> comparisonKeys(Collection<String> ids,String pattern,Pageable page) {
        String where="where r.visitId in :ids"+RESULT_MATCH;
        String select="select distinct new "+DTO+"IndicatorKey(r.valueKey,i.id,i.code,i.name,i.category,i.valueType,r.unit,r.method)";
        return page(select+RESULT_FROM+where+"order by i.category,i.name,r.valueKey","select count(distinct r.valueKey)"+RESULT_FROM+where,IndicatorKey.class,Map.of("ids",ids,"pattern",pattern),page);
    }
    public List<Measurement> comparisonValues(Collection<String> ids,Collection<String> keys) {
        if(keys.isEmpty()) return List.of();
        return query(MEASUREMENT+RESULT_FROM+"where r.visitId in :ids and r.valueKey in :keys",Measurement.class,Map.of("ids",ids,"keys",keys)).getResultList();
    }
    public List<Classification> classifications(Collection<String> ids) {
        if(ids.isEmpty())return List.of();
        return query("select new "+DTO+"Classification(r.visitId,r.rawValue)"+RESULT_FROM+"where r.visitId in :ids and i.code='HEALTH_CLASSIFICATION'",Classification.class,Map.of("ids",ids)).getResultList();
    }
    private <T> Page<T> page(String hql,String count,Class<T> type,Map<String,Object> args,Pageable page) {
        var values=query(hql,type,args).setFirstResult(Math.toIntExact(page.getOffset())).setMaxResults(page.getPageSize()).getResultList();
        // The count query may not need :year when no data constraint was selected.
        var counter=em.createQuery(count,Long.class);bind(counter,args);
        return new PageImpl<>(values,page,counter.getSingleResult());
    }
    private <T> TypedQuery<T> query(String hql,Class<T> type,Map<String,Object> args){var query=em.createQuery(hql,type);bind(query,args);return query;}
    private static void bind(Query query,Map<String,Object> args){var names=query.getParameters().stream().map(Parameter::getName).toList();args.forEach((key,value)->{if(names.contains(key))query.setParameter(key,value);});}
    private static Map<String,Object> nullableArgs(String key,Object value,String other,Object nullable){var result=new HashMap<String,Object>();result.put(key,value);result.put(other,nullable);return result;}
}
