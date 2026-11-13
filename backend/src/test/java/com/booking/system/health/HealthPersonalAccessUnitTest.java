package com.booking.system.health;

import com.booking.system.health.service.*;
import com.booking.system.health.repository.HealthPersonalAccessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthPersonalAccessUnitTest {
    @Test void publicFlagIsIndependentAndOffByDefaultEvenWhenInternalHealthIsEnabled() {
        var repository=mock(HealthPersonalAccessRepository.class);
        var service=new HealthPersonalAccessService(new HealthAccess(true),repository,mock(HealthAuditService.class),new HealthLookupLimiter());
        assertThatThrownBy(()->service.owner("X".repeat(43))).hasMessageContaining("chưa được bật");verifyNoInteractions(repository);
        ReflectionTestUtils.setField(service,"enabled",true);
        assertThatThrownBy(()->service.owner("not-a-session")).hasMessageContaining("Không thể xác minh");verifyNoInteractions(repository);
    }
    @Test void limiterFailsClosedAtCapacityInsteadOfEvictingActiveLimits() {
        var limiter=new HealthLookupLimiter();
        @SuppressWarnings("unchecked") var entries=(Map<String,Object>)ReflectionTestUtils.getField(limiter,"attempts");
        limiter.attempt("fixture-ip","fixture-code");Object counter=entries.values().iterator().next();
        for(int i=0;i<10000;i++)entries.put("fixture-"+i,counter);
        int before=entries.size();assertThatThrownBy(()->limiter.attempt("new-ip","new-code")).hasMessageContaining("15 phút");assertThat(entries).hasSize(before);
    }
}
