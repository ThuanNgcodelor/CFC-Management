package com.booking.system.infrastructure.service;

import com.booking.system.infrastructure.api.RouterConnectionDtos.*;
import com.booking.system.infrastructure.config.InfrastructureProperties;
import com.booking.system.infrastructure.repository.RouterConnectionRepository;
import com.booking.system.infrastructure.repository.RouterConnectionRepository.Stored;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouterConnectionServiceTest {
    @TempDir Path directory;
    RouterConnectionRepository repository;
    RouterCredentialCipher cipher;
    RouterConnectionService service;

    @BeforeEach void setup() {
        repository = mock(RouterConnectionRepository.class);
        when(repository.find()).thenReturn(Optional.empty());
        cipher = new RouterCredentialCipher(directory.resolve("credential.key").toString());
        service = new RouterConnectionService(repository, cipher,
                new InfrastructureProperties(false, "https://192.168.1.1", "", "", "", 60000));
    }

    @Test void encryptsWithRandomNoncePersistsKeyAndDetectsTampering() throws Exception {
        String first = cipher.encrypt("test-secret");
        String second = cipher.encrypt("test-secret");
        assertThat(first).doesNotContain("test-secret").isNotEqualTo(second);
        assertThat(new RouterCredentialCipher(directory.resolve("credential.key").toString()).decrypt(first)).isEqualTo("test-secret");
        assertThat(Files.getPosixFilePermissions(directory.resolve("credential.key")))
                .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        byte[] tampered = java.util.Base64.getDecoder().decode(first);
        tampered[15] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(java.util.Base64.getEncoder().encodeToString(tampered))).isInstanceOf(IllegalStateException.class);
        Files.delete(directory.resolve("credential.key"));
        assertThatThrownBy(() -> cipher.decrypt(first)).isInstanceOf(IllegalStateException.class);
        assertThat(directory.resolve("credential.key")).doesNotExist();
    }

    @Test void requiresInitialPasswordAndOnlyPreservesItForSameIdentity() {
        assertThatThrownBy(() -> service.candidate(new Request("https://192.168.1.1", "reader", "", true, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        when(repository.find()).thenReturn(Optional.of(new Stored("https://192.168.1.1", "reader", cipher.encrypt("saved-secret"), true, 4)));
        assertThat(service.candidate(new Request("https://192.168.1.1/", "reader", "", true, 4)).password()).isEqualTo("saved-secret");
        assertThatThrownBy(() -> service.candidate(new Request("https://192.168.1.2", "reader", "", true, 4))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.candidate(new Request("https://192.168.1.1", "other", "", true, 4))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.candidate(new Request("https://192.168.1.1", "reader", "new", true, 3)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test void savesCiphertextAndNeverSerializesPasswordOrEncryptedValue() throws Exception {
        service.save(new Request("https://192.168.1.1/", "reader", "unique-test-secret", true, 0), "admin", "Admin");
        var captor = ArgumentCaptor.forClass(Stored.class);
        verify(repository).save(captor.capture(), eq("192.168.1.1"), eq("admin"), eq("Admin"));
        assertThat(cipher.decrypt(captor.getValue().encryptedPassword())).isEqualTo("unique-test-secret");
        when(repository.find()).thenReturn(Optional.of(captor.getValue()));
        String json = new ObjectMapper().writeValueAsString(service.metadata());
        assertThat(json).doesNotContain("unique-test-secret", "encryptedPassword", captor.getValue().encryptedPassword());
        assertThat(service.effective().password()).isEqualTo("unique-test-secret");
        assertThat(service.effective().toString()).doesNotContain("unique-test-secret");
        assertThat(new ObjectMapper().writeValueAsString(new Request("https://192.168.1.1", "reader", "unique-test-secret", true, 0)))
                .doesNotContain("unique-test-secret");
    }

    @Test void restrictsTargetsToPrivateHttpsWithoutCredentialsOrPaths() {
        for (String url : new String[]{"http://192.168.1.1", "https://127.0.0.1", "https://169.254.169.254", "https://8.8.8.8", "https://router.local", "https://user:pass@192.168.1.1", "https://192.168.1.1/rest", "https://192.168.1.1?q=x", "https://192.168.1.1#x", "https://192.168.01.1", "https://192.168.1.999"}) {
            assertThatThrownBy(() -> RouterConnectionService.normalizeUrl(url)).as(url).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(RouterConnectionService.normalizeUrl("https://10.0.0.1:8443/")).isEqualTo("https://10.0.0.1:8443");
        assertThat(RouterConnectionService.normalizeUrl("https://172.31.0.1")).isEqualTo("https://172.31.0.1");
    }
}
