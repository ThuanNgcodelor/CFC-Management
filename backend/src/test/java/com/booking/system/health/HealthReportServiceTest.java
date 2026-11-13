package com.booking.system.health;

import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.importer.HrImportActor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class HealthReportServiceTest {
    @Test void onlyOneFileRendersAtATimeAndTheSlotIsReleasedAfterFailure() throws Exception {
        var snapshots=mock(HealthReportSnapshot.class);var service=new HealthReportService(snapshots,new HealthAccess(true));
        var actor=new HrImportActor("fake","Fake","ADMIN");var request=new Request(null,2023,2026,null,null,"ALL","codeAsc",null);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(snapshots.read(request,actor)).thenAnswer(invocation->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Fixture timeout");throw new IllegalStateException("Fixture failure");});
        try(var executor=Executors.newSingleThreadExecutor()) {
            var result=executor.submit(()->service.export(request,false,actor));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            try{assertThatThrownBy(()->service.export(request,false,actor)).hasMessageContaining("Đang tạo");}finally{release.countDown();}
            assertThatThrownBy(()->result.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
        }
        var empty=new Snapshot(request,List.of(),List.of(),List.of(),List.of(),new Summary(0,0,0,0,List.of(),List.of()));
        doReturn(empty).when(snapshots).read(request,actor);assertThat(service.export(request,false,actor)).isNotEmpty();
        assertThatThrownBy(()->service.export(request,true,new HrImportActor("fake","Fake","HR_STAFF"))).hasMessageContaining("Chỉ quản trị");
    }
}
