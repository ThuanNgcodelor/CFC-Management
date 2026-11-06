package com.booking.system.controller;

import com.booking.system.infrastructure.api.*;
import com.booking.system.infrastructure.api.RouterConnectionDtos.Response;
import com.booking.system.infrastructure.service.*;
import com.booking.system.entity.User;
import com.booking.system.enums.*;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtUtils;
import jakarta.servlet.Filter;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RouterConnectionSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        when(context.getBean(RouterConnectionService.class).metadata()).thenReturn(new Response("https://192.168.1.1", "reader", true, true, 1, "WEB"));
    }
    @AfterEach void close() { context.close(); }
    @Test void realConnectionEndpointsAreAdminOnly() throws Exception {
        for (String path : new String[]{"/connection", "/connection/test", "/refresh"}) {
            var anonymous = path.equals("/connection") ? get("/api/v1/infrastructure/router" + path) : post("/api/v1/infrastructure/router" + path);
            mvc.perform(anonymous).andExpect(status().isUnauthorized());
        }
        for (RoleEnum role : new RoleEnum[]{RoleEnum.MANAGER, RoleEnum.HR_STAFF}) {
            token(role);
            mvc.perform(get("/api/v1/infrastructure/router/connection").header("Authorization", "Bearer token")).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/infrastructure/router/connection").header("Authorization", "Bearer token").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/infrastructure/router/connection/test").header("Authorization", "Bearer token")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/infrastructure/router/refresh").header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        }
        token(RoleEnum.ADMIN);
        mvc.perform(get("/api/v1/infrastructure/router/connection").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.passwordConfigured").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.encryptedPassword").doesNotExist());
        mvc.perform(put("/api/v1/infrastructure/router/connection").header("Authorization", "Bearer token").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        var service = context.getBean(RouterConnectionService.class);
        verify(service, never()).save(any(), any(), any());
        var metadata = service.metadata();
        when(service.save(any(), any(), any())).thenReturn(metadata);
        mvc.perform(put("/api/v1/infrastructure/router/connection").header("Authorization", "Bearer token")
                .contentType("application/json").content("{\"baseUrl\":\"https://192.168.1.1\",\"username\":\"reader\",\"password\":\"synthetic-secret\",\"enabled\":true,\"rowVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
        var captured = org.mockito.ArgumentCaptor.forClass(com.booking.system.infrastructure.api.RouterConnectionDtos.Request.class);
        verify(service).save(captured.capture(), eq("test-admin"), eq("test@example.test"));
        org.assertj.core.api.Assertions.assertThat(captured.getValue().password()).isEqualTo("synthetic-secret");
    }
    void token(RoleEnum role) {
        User user = new User(); user.setId("test-admin"); user.setEmail("test@example.test"); user.setRole(role); user.setStatus(UserStatus.ACTIVE);
        when(context.getBean(JwtUtils.class).validateJwtToken("token")).thenReturn(true);
        when(context.getBean(JwtUtils.class).getEmailFromJwtToken("token")).thenReturn(user.getEmail());
        when(context.getBean(UserRepository.class).findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }
    @Configuration
    @Import({DashboardSecurityContractTest.TestWebConfig.class, RouterConnectionController.class, InfrastructureExceptionHandler.class})
    static class Config {
        @Bean RouterConnectionService connections() { return mock(RouterConnectionService.class); }
        @Bean RouterOsClient client() { return mock(RouterOsClient.class); }
        @Bean InfrastructureService infrastructure() { return mock(InfrastructureService.class); }
    }
}
