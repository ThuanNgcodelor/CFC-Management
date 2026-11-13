package com.booking.system.health.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class HealthPersonalAccessRepository {
    private final JdbcTemplate jdbc;
    public record Grant(String employee,String code,String citizen,String hash,String generation,LocalDateTime expires,boolean revoked,boolean allowIdentityOnly) {
        @Override public String toString(){return "HealthAccessGrant[redacted]";}
    }
    public Optional<Grant> grantByCode(String code) {
        return jdbc.query("SELECT e.id,e.employee_code,i.citizen_identity_number,g.secret_hash,g.generation,g.expires_at,g.revoked,g.allow_identity_only FROM hr_employees e JOIN hr_employee_identity i ON i.employee_id=e.id JOIN hr_health_access_grants g ON g.employee_id=e.id WHERE e.employee_code=? FOR UPDATE",
            (rs,n)->new Grant(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toLocalDateTime(),rs.getBoolean(7),rs.getBoolean(8)),code).stream().findFirst();
    }
    public Optional<Grant> grantBySecretHash(String secretHash) {
        return jdbc.query("SELECT employee_id,secret_hash,generation,expires_at,revoked,allow_identity_only FROM hr_health_access_grants WHERE secret_hash=?",
            (rs,n)->new Grant(rs.getString(1),null,null,rs.getString(2),rs.getString(3),rs.getTimestamp(4).toLocalDateTime(),rs.getBoolean(5),rs.getBoolean(6)),secretHash).stream().findFirst();
    }
    public boolean eligible(String employee) {
        var values=jdbc.query("SELECT i.citizen_identity_number FROM hr_employee_identity i WHERE i.employee_id=?",(rs,n)->rs.getString(1),employee);
        return !values.isEmpty()&&values.getFirst()!=null&&values.getFirst().strip().matches("[0-9]{12}");
    }
    public boolean lockEmployee(String employee) {
        return !jdbc.query("SELECT id FROM hr_employees WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),employee).isEmpty();
    }
    public String lockEligible(String employee) {
        if(!eligible(employee))return null;
        var ids=jdbc.query("SELECT e.id FROM hr_employees e JOIN hr_employee_identity i ON i.employee_id=e.id WHERE e.id=? AND i.citizen_identity_number IS NOT NULL AND LENGTH(TRIM(i.citizen_identity_number))=12 FOR UPDATE",(rs,n)->rs.getString(1),employee);
        return ids.isEmpty()?null:ids.getFirst();
    }
    public Optional<Grant> grant(String employee) {
        return jdbc.query("SELECT employee_id,secret_hash,generation,expires_at,revoked,allow_identity_only FROM hr_health_access_grants WHERE employee_id=?",(rs,n)->new Grant(rs.getString(1),null,null,rs.getString(2),rs.getString(3),rs.getTimestamp(4).toLocalDateTime(),rs.getBoolean(5),rs.getBoolean(6)),employee).stream().findFirst();
    }
    public void issue(String employee,String hash,String generation,LocalDateTime expires,LocalDateTime now) {
        int changed=jdbc.update("UPDATE hr_health_access_grants SET secret_hash=?,generation=?,expires_at=?,revoked=FALSE,allow_identity_only=FALSE,updated_at=? WHERE employee_id=?",hash,generation,expires,now,employee);
        if(changed==0)jdbc.update("INSERT INTO hr_health_access_grants(employee_id,secret_hash,generation,expires_at,revoked,updated_at) VALUES(?,?,?,?,FALSE,?)",employee,hash,generation,expires,now);
        clearSessions(employee);
    }
    public void policy(String employee,boolean allowed,String generation,LocalDateTime now) {
        jdbc.update("UPDATE hr_health_access_grants SET allow_identity_only=?,generation=?,updated_at=? WHERE employee_id=?",allowed,generation,now,employee);clearSessions(employee);
    }
    public void revoke(String employee,LocalDateTime now) {
        jdbc.update("UPDATE hr_health_access_grants SET revoked=TRUE,generation=?,updated_at=? WHERE employee_id=?",UUID.randomUUID().toString(),now,employee);clearSessions(employee);
    }
    public void clearSessions(String employee){jdbc.update("DELETE FROM hr_health_access_sessions WHERE employee_id=?",employee);}
    public void session(String hash,String employee,String generation,LocalDateTime expires,LocalDateTime now) {
        jdbc.update("DELETE FROM hr_health_access_sessions WHERE expires_at<=?",now);
        // A new lookup replaces previous sessions, bounding storage and supporting shared-device recovery.
        clearSessions(employee);
        jdbc.update("INSERT INTO hr_health_access_sessions(token_hash,employee_id,generation,expires_at) VALUES(?,?,?,?)",hash,employee,generation,expires);
    }
    public Optional<String> owner(String hash,LocalDateTime now) {
        return jdbc.query("SELECT s.employee_id FROM hr_health_access_sessions s JOIN hr_health_access_grants g ON g.employee_id=s.employee_id WHERE s.token_hash=? AND s.expires_at>? AND g.expires_at>? AND g.revoked=FALSE AND g.generation=s.generation",(rs,n)->rs.getString(1),hash,now,now).stream().findFirst();
    }
    public void logout(String hash){jdbc.update("DELETE FROM hr_health_access_sessions WHERE token_hash=?",hash);}
}
