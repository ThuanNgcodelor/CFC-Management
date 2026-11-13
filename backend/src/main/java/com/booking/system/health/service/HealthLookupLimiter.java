package com.booking.system.health.service;

import org.springframework.stereotype.Component;
import com.booking.system.hr.api.HrApiException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

/** Single-instance abuse protection. Never trusts forwarded IP headers supplied by clients. */
@Component
public class HealthLookupLimiter {
    private static final int MAX_KEYS=10000, IP_ATTEMPTS=20, CODE_ATTEMPTS=10;
    private static final Duration WINDOW=Duration.ofMinutes(15);
    private final Map<String,Counter> attempts=new HashMap<>();
    private record Counter(int count,Instant until) { }
    public synchronized void attempt(String ip,String code) {
        Instant now=Instant.now();attempts.entrySet().removeIf(e->!e.getValue().until().isAfter(now));
        String ipKey="ip:"+digest(ip),codeKey="code:"+digest(code);
        if(attempts.size()>=MAX_KEYS && (!attempts.containsKey(ipKey)||!attempts.containsKey(codeKey)))throw limited();
        boolean ipAllowed=bump(ipKey,IP_ATTEMPTS,now),codeAllowed=bump(codeKey,CODE_ATTEMPTS,now);
        if(!ipAllowed||!codeAllowed)throw limited();
    }
    private boolean bump(String key,int max,Instant now) {
        var old=attempts.getOrDefault(key,new Counter(0,now.plus(WINDOW)));attempts.put(key,new Counter(Math.min(old.count()+1,max+1),old.until()));return old.count()<max;
    }
    private static String digest(String value){return com.booking.system.health.importer.HealthImportSupport.hash(value==null?"":value);}
    private static HrApiException limited(){return new HrApiException(HttpStatus.TOO_MANY_REQUESTS,"HEALTH_SELF_LIMIT","Đã vượt số lần tra cứu; vui lòng thử lại sau 15 phút hoặc liên hệ nhân sự.");}
}
