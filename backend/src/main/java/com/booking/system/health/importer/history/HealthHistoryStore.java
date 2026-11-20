package com.booking.system.health.importer.history;

import com.booking.system.health.importer.HealthImportSupport;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@lombok.extern.slf4j.Slf4j
@Component
public class HealthHistoryStore {
    public static final String BUNDLE="health/history/company-history-v1.bundle";
    private final Resource resource;
    private final Path keyFile;
    @Autowired public HealthHistoryStore(@Value("${cfc.health.history.key-file:${user.home}/.local/share/cfcbase/health/history-import.key}") String path) {this(new ClassPathResource(BUNDLE),Path.of(path));}
    public HealthHistoryStore(Resource resource,Path keyFile){this.resource=resource;this.keyFile=keyFile.toAbsolutePath().normalize();}
    public record Opened(String hash,HealthHistoryCrypto.Package data) { }
    public record Receipt(String hash,List<String> batchIds) { }
    public boolean available(){return resource.exists();}
    public boolean unlocked(){return Files.isRegularFile(keyFile,LinkOption.NOFOLLOW_LINKS);}
    public Opened open() {
        try {byte[] key=readKey();try{return decrypt(key);}finally{Arrays.fill(key,(byte)0);}}
        catch(IOException exception){throw new IllegalStateException("Không đọc được khóa lịch sử đã cài.",exception);}
    }
    private Opened decrypt(byte[] key) {
        try(var input=resource.getInputStream()) {
            byte[] bytes=input.readNBytes(HealthHistoryCrypto.MAX_BYTES+1);
            return new Opened(HealthImportSupport.hash(bytes),HealthHistoryCrypto.open(bytes,key));
        } catch(Exception exception){log.warn("Health history bundle rejected: errorType={}",exception.getClass().getSimpleName());throw new IllegalArgumentException("Khóa không phù hợp hoặc gói dữ liệu lịch sử không hợp lệ.");}
    }
    public synchronized void install(byte[] key) {
        decrypt(key);
        try {
            if(Files.exists(keyFile,LinkOption.NOFOLLOW_LINKS)) {
                byte[] existing=readKey();try{if(!java.security.MessageDigest.isEqual(existing,key))throw new IllegalArgumentException("Khóa khác đã tồn tại; cần quản trị kiểm tra trước khi thay khóa.");}finally{Arrays.fill(existing,(byte)0);}
                return;
            }
            writePrivate(keyFile,key);
        } catch(IOException exception){throw new IllegalStateException("Không lưu được khóa lịch sử tại thư mục riêng của máy chủ.",exception);}
    }
    private byte[] readKey() throws IOException {
        if(Files.isSymbolicLink(keyFile)||Files.size(keyFile)!=32)throw new IOException("Invalid private key file");
        privatePermissions(keyFile,false);return Files.readAllBytes(keyFile);
    }
    public Receipt receipt() {
        var file=keyFile.resolveSibling("history-import-receipt.json");if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null;
        try {
            if(Files.isSymbolicLink(file)||Files.size(file)>8192)throw new IOException("Invalid receipt");
            privatePermissions(file,false);var result=HealthImportSupport.read(Files.readString(file),Receipt.class);
            if(result.hash()==null||!result.hash().matches("[a-f0-9]{64}")||result.batchIds()==null||result.batchIds().size()>20)throw new IOException("Invalid receipt");
            return result;
        } catch(IOException exception){throw new IllegalStateException("Không đọc được trạng thái nhập lịch sử.",exception);}
    }
    public void receipt(Receipt value) {
        try{writePrivate(keyFile.resolveSibling("history-import-receipt.json"),HealthImportSupport.json(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        catch(IOException exception){throw new IllegalStateException("Không ghi được trạng thái nhập lịch sử; có thể thử lại an toàn.",exception);}
    }
    private static void writePrivate(Path target,byte[] bytes) throws IOException {
        Path parent=target.getParent();
        if(parent.getParent()==null||parent.equals(Path.of("/tmp")))throw new IOException("A dedicated private directory is required");
        if(!Files.exists(parent))Files.createDirectories(parent,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if(Files.isSymbolicLink(parent)||Files.isSymbolicLink(target))throw new IOException("Symlinks are not supported");
        privatePermissions(parent,true);
        Path temp=Files.createTempFile(parent,"history-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try{Files.write(temp,bytes);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
    private static void privatePermissions(Path file,boolean directory) throws IOException {
        var actual=Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS);
        var allowed=PosixFilePermissions.fromString(directory?"rwx------":"rw-------");
        if(!allowed.containsAll(actual))throw new IOException("History files must not be readable by other users");
    }
}
