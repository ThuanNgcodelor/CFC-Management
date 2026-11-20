package com.booking.system.health;

import com.booking.system.health.importer.*;
import com.booking.system.health.importer.baseline.*;
import com.booking.system.health.importer.history.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import static org.assertj.core.api.Assertions.*;

class HealthHistoryCryptoTest {
    @TempDir Path directory;
    HealthHistoryCrypto.Package data() throws Exception {
        var input=HealthBaselineAnalysisTest.fixture("Tổng hợp","010123-FAKE","Người giả lập","QA01","20","II");
        return new HealthHistoryCrypto.Package(1,true,new HealthBaselineReader(new HealthWorkbookParser()).read(List.of(input)));
    }
    @Test void encryptedBundleRoundTripsWithoutExposingNamesOrValuesAndRejectsTampering() throws Exception {
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
        byte[] bytes=HealthHistoryCrypto.seal(data(),key);
        assertThat(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("QA01","010123-FAKE","rawValue");
        assertThat(HealthHistoryCrypto.open(bytes,key).dataset().sources().getFirst().rows().getFirst().code()).isEqualTo("QA01");
        byte[] wrong=key.clone();wrong[0]^=1;
        assertThatThrownBy(()->HealthHistoryCrypto.open(bytes,wrong)).isInstanceOf(java.security.GeneralSecurityException.class);
        bytes[bytes.length-1]^=1;
        assertThatThrownBy(()->HealthHistoryCrypto.open(bytes,key)).isInstanceOf(java.security.GeneralSecurityException.class);
    }
    @Test void installChecksKeyBeforeWritingAndKeepsKeyAndReceiptPrivate() throws Exception {
        byte[] key=new byte[32];Arrays.fill(key,(byte)9);
        Path bundle=directory.resolve("history.bundle"),file=directory.resolve("private/history.key");
        Files.write(bundle,HealthHistoryCrypto.seal(data(),key));
        var store=new HealthHistoryStore(new FileSystemResource(bundle),file);
        assertThatThrownBy(()->store.install(new byte[32])).hasMessageContaining("Khóa không phù hợp");assertThat(file).doesNotExist();
        store.install(key);assertThat(store.open().data().dataset().sources()).hasSize(1);
        store.receipt(new HealthHistoryStore.Receipt("a".repeat(64),List.of("fake-batch")));
        assertThat(store.receipt().batchIds()).containsExactly("fake-batch");
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.getPosixFilePermissions(file.getParent())).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        store.install(key);assertThat(Files.readAllBytes(file)).isEqualTo(key);
    }
}
