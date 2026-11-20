package com.booking.system.health;

import com.booking.system.health.importer.*;
import com.booking.system.health.importer.baseline.*;
import com.booking.system.health.importer.history.HealthHistoryCrypto;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

/** Offline packaging tool. Prints counts/paths only; plaintext and the key never enter the repository. */
public final class HealthHistoryBundleTool {
    private HealthHistoryBundleTool() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Cần thư mục3 workbook, đường dẫn bundle, đường dẫn khóa riêng ngoài repo.");
        Path input=Path.of(args[0]).toAbsolutePath().normalize(),output=Path.of(args[1]).toAbsolutePath().normalize(),keyFile=Path.of(args[2]).toAbsolutePath().normalize();
        if(keyFile.startsWith(input)||keyFile.startsWith(Path.of("").toAbsolutePath().normalize()))throw new IllegalArgumentException("Khóa phải ở thư mục riêng ngoài repository.");
        Files.createDirectories(keyFile.getParent(),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        byte[] key;
        if(Files.exists(keyFile)) {
            if(Files.isSymbolicLink(keyFile)||Files.size(keyFile)!=32||!PosixFilePermissions.fromString("rw-------").containsAll(Files.getPosixFilePermissions(keyFile)))throw new IllegalArgumentException("Khóa cũ không hợp lệ hoặc quyền tệp không riêng tư.");
            key=Files.readAllBytes(keyFile);
        } else {
            key=new byte[32];new SecureRandom().nextBytes(key);Files.createFile(keyFile,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));Files.write(keyFile,key);
        }
        try {
            var inputs=List.of(new WorkbookInput(Kind.HISTORY_2023_2024,"Nguồn lịch sử2023–2024",Files.readAllBytes(input.resolve("So sánh kết quả sức khỏe gián tiếp 2023 - 2024.xlsx"))),
                new WorkbookInput(Kind.SUPPLEMENT_2024,"Nguồn bổ sung2024",Files.readAllBytes(input.resolve("bo sung thong tin SK 2024.xlsx"))),
                new WorkbookInput(Kind.RESULTS_2025,"Nguồn lịch sử2025",Files.readAllBytes(input.resolve("KHAM SUC KHOE 2025.xlsx"))));
            var dataset=new HealthBaselineReader(new HealthWorkbookParser()).read(inputs);
            var data=new HealthHistoryCrypto.Package(1,true,dataset);
            if(Files.exists(output)) {
                var previous=HealthHistoryCrypto.open(Files.readAllBytes(output),key);
                if(HealthImportSupport.json(previous).equals(HealthImportSupport.json(data))){System.out.println("HEALTH_HISTORY_BUNDLE_UNCHANGED");return;}
            }
            byte[] encrypted=HealthHistoryCrypto.seal(data,key);
            var checked=HealthHistoryCrypto.open(encrypted,key);
            if(!HealthImportSupport.json(data).equals(HealthImportSupport.json(checked)))throw new IllegalStateException("Kiểm chứng giải mã chưa khớp nguồn.");
            Files.createDirectories(output.getParent());Files.write(output,encrypted);
            System.out.println("HEALTH_HISTORY_BUNDLE_READY sources="+dataset.sources().size()+" rows="+dataset.sources().stream().mapToInt(s->s.rows().size()).sum()+" encryptedBytes="+encrypted.length);
            System.out.println("Private key file (never commit): "+keyFile);
        } finally {Arrays.fill(key,(byte)0);}
    }
}
