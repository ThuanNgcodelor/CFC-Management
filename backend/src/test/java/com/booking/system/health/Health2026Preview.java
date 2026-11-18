package com.booking.system.health;

import com.booking.system.health.api.HealthPersonalAccessDtos.*;
import com.booking.system.health.service.HealthPersonalAccessService;
import com.booking.system.entity.User;
import com.booking.system.enums.*;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtUtils;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.springframework.boot.SpringApplication;
import static org.mockito.Mockito.*;

/** Test classpath only: synthetic 2024–2026 pilot with real health APIs and memory storage. */
public final class Health2026Preview {
    private Health2026Preview() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Chọn thư mục /tmp/cfc-health-2026-* riêng.");
        var folder=Path.of(args[0]).toAbsolutePath().normalize();
        if(!folder.startsWith(Path.of("/tmp"))||!folder.getFileName().toString().startsWith("cfc-health-2026-"))throw new IllegalArgumentException("Chỉ dùng thư mục QA trong /tmp.");
        Files.createDirectories(folder);Files.setPosixFilePermissions(folder,PosixFilePermissions.fromString("rwx------"));
        var app=new SpringApplication(HealthPhase8Preview.Config.class);
        var context=app.run("--spring.config.location=optional:file:/tmp/cfc-health-2026-unused.properties","--spring.config.import=",
            "--server.address=127.0.0.1","--server.port=18086",
            "--spring.datasource.url=jdbc:h2:mem:health_2026_preview;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--spring.jpa.hibernate.ddl-auto=validate","--spring.jpa.show-sql=false","--spring.jpa.open-in-view=false","--spring.flyway.enabled=true",
            "--cfc.health.enabled=true","--cfc.health.self-service.enabled=true","--app.cors.allowed-origins=http://127.0.0.1:5196",
            "--logging.level.root=WARN","--logging.level.org.springframework.jdbc=INFO","--logging.level.org.springframework.boot.security.autoconfigure=ERROR");
        var fixture=new Health2026ReadinessTest();context.getAutowireCapableBeanFactory().autowireBean(fixture);fixture.setup();fixture.importHistory();
        fixture.confirmAll(fixture.stage(2026,true,List.of(Health2026WorkbookFixtures.entry(2026,"HEALTHDEV01","24","<0.5"),Health2026WorkbookFixtures.entry(2026,"HEALTHDEV02","25",""))));
        String current=fixture.fixture.campaignId;
        for(int year:List.of(2024,2025,2026)){fixture.fixture.campaignId=fixture.years.get(year);fixture.release();}
        fixture.fixture.campaignId=current;
        fixture.stage(2026,true,List.of(Health2026WorkbookFixtures.entry(2026,"HEALTHDEV01","26","<0.5")));
        fixture.stage(2026,true,List.of(Health2026WorkbookFixtures.entry(2026,"KHONG-TON-TAI","24",""),new Health2026WorkbookFixtures.Entry("HEALTHDEV02","","QA-INVALID","31/02/2026","25","")));
        var jdbc=fixture.fixture.jdbc;
        jdbc.update("INSERT INTO hr_employee_identity(employee_id,citizen_identity_number,verification_status,created_by_actor,updated_by_actor,row_version) VALUES(?,'000000000001','UNVERIFIED','QA','QA',0)",fixture.fixture.employeeId);
        var access=context.getBean(HealthPersonalAccessService.class);
        var issued=access.issue(fixture.fixture.employeeId,new Change(null,"Cấp mã demo giả lập"),HealthPhase2Test.ADMIN);
        access.policy(fixture.fixture.employeeId,new LookupPolicy(access.status(fixture.fixture.employeeId,HealthPhase2Test.ADMIN).generation(),"Cho phép demo giả lập",true),HealthPhase2Test.ADMIN);
        for(var role:List.of(RoleEnum.ADMIN,RoleEnum.MANAGER,RoleEnum.HR_STAFF)) {
            var user=new User();user.setId("health-demo-"+role);user.setEmail(role.name().toLowerCase()+"@example.test");user.setFullName("Quản trị giả lập "+role);user.setRole(role);user.setStatus(UserStatus.ACTIVE);
            var jwt=context.getBean(JwtUtils.class);when(jwt.validateJwtToken(role.name())).thenReturn(true);when(jwt.getEmailFromJwtToken(role.name())).thenReturn(user.getEmail());when(context.getBean(UserRepository.class).findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
        var ready=folder.resolve("ready.json");Files.writeString(ready,com.booking.system.health.importer.HealthImportSupport.json(Map.of("accessCode",issued.code(),"employeeCode","HEALTHDEV01","citizenId","000000000001","campaignId",current)));
        Files.setPosixFilePermissions(ready,PosixFilePermissions.fromString("rw-------"));
        Health2026WorkbookFixtures.main(new String[]{folder.toString()});
        System.out.println("HEALTH_2026_PREVIEW_READY loopback=18086 artifacts="+folder);
    }
}
