package com.booking.system.health;

import com.booking.system.entity.User;
import com.booking.system.enums.*;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtUtils;
import java.net.URI;
import java.net.http.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "server.address=127.0.0.1","spring.datasource.url=jdbc:h2:mem:health_http_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true"})
class HealthSecurityHttpTest {
    @LocalServerPort int port;
    @Autowired JwtUtils tokens;
    @Autowired UserRepository users;
    @Test void forbiddenRemains403AfterRealServletDispatchAndGuestsGetPrivateJson401() throws Exception {
        var user=new User();user.setId("qa-http-staff");user.setEmail("staff-http@example.test");user.setFullName("Fixture staff");user.setRole(RoleEnum.HR_STAFF);user.setStatus(UserStatus.ACTIVE);
        when(tokens.validateJwtToken("STAFF_HTTP")).thenReturn(true);when(tokens.getEmailFromJwtToken("STAFF_HTTP")).thenReturn(user.getEmail());when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        try(var client=HttpClient.newHttpClient()) {
            for(boolean authenticated:new boolean[]{false,true}) {
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/hr/health/capabilities"));
                if(authenticated)request.header("Authorization","Bearer STAFF_HTTP");
                var response=client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
                int expected=authenticated?403:401;
                assertThat(response.statusCode()).isEqualTo(expected);
                assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/json");
                assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");
                assertThat(response.body()).contains("\"status\":"+expected).doesNotContain("SQL","STAFF_HTTP");
            }
        }
    }
}
