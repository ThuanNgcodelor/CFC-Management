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
import org.springframework.context.annotation.*;
import static org.mockito.Mockito.*;

/** Test-source-only fake pilot. No database URL or bind address may be supplied by the caller. */
public class HealthPhase8Preview {
    @Configuration(proxyBeanMethods=false)
    @Import({HealthPhase2Test.Config.class,com.booking.system.config.WebConfig.class})
    static class Config {
        @Bean org.springframework.test.web.servlet.MockMvc fixtureMvc(){return mock(org.springframework.test.web.servlet.MockMvc.class);}
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Supply a private /tmp output directory");
        Path output=Path.of(args[0]).toAbsolutePath().normalize();
        if(!output.startsWith(Path.of("/tmp"))||!output.getFileName().toString().startsWith("cfc-health-phase8-"))throw new IllegalArgumentException("Only private phase8 /tmp artifacts are supported");
        Files.createDirectories(output);Files.setPosixFilePermissions(output,PosixFilePermissions.fromString("rwx------"));
        var app=new SpringApplication(Config.class);
        var context=app.run("--spring.config.location=optional:file:/tmp/cfc-health-phase8-no-config.properties","--spring.config.import=",
            "--server.address=127.0.0.1","--server.port=18086",
            "--spring.datasource.url=jdbc:h2:mem:health_phase8_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--spring.jpa.hibernate.ddl-auto=validate","--spring.jpa.show-sql=false","--spring.jpa.open-in-view=false","--spring.flyway.enabled=true",
            "--cfc.health.enabled=true","--cfc.health.history.enabled=false","--cfc.health.self-service.enabled=true","--app.cors.allowed-origins=http://127.0.0.1:5196",
            "--logging.level.root=WARN","--logging.level.org.springframework.boot.security.autoconfigure=ERROR");
        var logging=org.springframework.boot.logging.LoggingSystem.get(HealthPhase8Preview.class.getClassLoader());
        for(String logger:List.of("ROOT","org.springframework","org.hibernate","com.booking"))logging.setLogLevel(logger,org.springframework.boot.logging.LogLevel.WARN);
        var fixture=new HealthPublicationTest();context.getAutowireCapableBeanFactory().autowireBean(fixture);fixture.fixtures();fixture.released();
        var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        jdbc.update("INSERT INTO hr_employee_identity(employee_id,citizen_identity_number,verification_status,created_by_actor,updated_by_actor,row_version) VALUES(?,'000000000001','UNVERIFIED','QA','QA',0)",fixture.employeeId);
        var access=context.getBean(HealthPersonalAccessService.class);
        var issued=access.issue(fixture.employeeId,new Change(null,"Mã pilot giả lập"),HealthPublicationTest.ADMIN);
        access.policy(fixture.employeeId,new LookupPolicy(access.status(fixture.employeeId,HealthPublicationTest.ADMIN).generation(),"Cho phép pilot giả lập",true),HealthPublicationTest.ADMIN);
        String admin=UUID.randomUUID().toString();var user=new User();user.setId("health-phase8-admin");user.setEmail("phase8@example.test");user.setFullName("Quản trị giả lập");user.setRole(RoleEnum.ADMIN);user.setStatus(UserStatus.ACTIVE);
        var tokens=context.getBean(JwtUtils.class);var users=context.getBean(UserRepository.class);
        when(tokens.validateJwtToken(admin)).thenReturn(true);when(tokens.getEmailFromJwtToken(admin)).thenReturn(user.getEmail());when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var own=fixture.releaseFor(fixture.visitIds.getFirst());var foreign=fixture.releaseFor(fixture.visitIds.get(2));
        var proof=Map.of("accessCode",issued.code(),"adminToken",admin,"employeeId",fixture.employeeId,"releaseId",own.id(),"releaseVersion",own.rowVersion(),"foreignReleaseId",foreign.id());
        var file=output.resolve("ready.json");Files.writeString(file,com.booking.system.health.importer.HealthImportSupport.json(proof));Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        System.out.println("HEALTH_PHASE8_FAKE_HTTP_READY loopback=18086 artifacts="+output);
    }
}
