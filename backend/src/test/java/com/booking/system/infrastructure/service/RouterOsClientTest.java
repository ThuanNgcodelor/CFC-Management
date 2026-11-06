package com.booking.system.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;

class RouterOsClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void readsRouterOsSingletonArrayAndStringValues() throws Exception {
        var resource = mapper.readTree("[{\"version\":\"7.16.2\",\"uptime\":\"51d09:20:43\",\"cpu-load\":\"12\",\"free-memory\":\"1000000\",\"total-memory\":\"2000000\"}]");
        var interfaces = mapper.readTree("[{\"running\":\"true\"},{\"running\":\"false\"}]");
        var result = RouterOsClient.parseHealth(resource, interfaces);
        assertThat(result.uptime()).isEqualTo("51d09:20:43");
        assertThat(result.cpuLoad()).isEqualTo(12);
        assertThat(result.freeMemory()).isEqualTo(1000000L);
        assertThat(result.interfacesUp()).isEqualTo(1);
        assertThat(result.interfacesDown()).isEqualTo(1);
        assertThat(RouterOsClient.parseHealth(resource.get(0), interfaces).softwareVersion()).isEqualTo("7.16.2");
        assertThatThrownBy(() -> RouterOsClient.parseHealth(mapper.readTree("[]"), interfaces)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void errorsDoNotExposeResponseBodiesOrSecrets() {
        var error = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "secret-value", null, "password=secret-value".getBytes(), null);
        assertThat(RouterOsClient.failureMessage(error)).contains("401").doesNotContain("secret-value");
        assertThat(RouterOsClient.failureMessage(new RuntimeException(new javax.net.ssl.SSLHandshakeException("secret-value")))).contains("Chứng thư").doesNotContain("secret-value");
    }
}
