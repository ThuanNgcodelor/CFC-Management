package com.booking.system.health.importer.baseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import static com.booking.system.health.importer.HealthImportSupport.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

public final class HealthBaselineReportWriter {
    private HealthBaselineReportWriter() { }
    public static void write(Path directory,Dataset dataset,Report report,List<WorkbookInput> originals) throws IOException {
        Files.createDirectories(directory);Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));
        writePrivate(directory.resolve("baseline-private.json"),json(dataset).getBytes(StandardCharsets.UTF_8));
        writePrivate(directory.resolve("baseline-report-private.json"),json(report).getBytes(StandardCharsets.UTF_8));
        var csv=new StringBuilder("\ufeffLoại,Sheet nguồn,Dòng,SID,Mã nhân viên,Nội dung,Ứng viên ID\r\n");
        for(var item:report.findings()) {
            var values=List.of(cell(item.kind()),cell(item.sourceKey()),cell(Integer.toString(item.sourceRow())),
                cell(item.sid()),cell(item.employeeCode()),cell(item.message()),cell(String.join(";",item.candidateEmployeeIds())));
            csv.append(String.join(",",values)).append("\r\n");
        }
        writePrivate(directory.resolve("baseline-review-private.csv"),csv.toString().getBytes(StandardCharsets.UTF_8));
        for(var input:originals) writePrivate(directory.resolve(input.kind().name()+".xlsx"),input.content());
    }
    public static void writePrivate(Path target,byte[] bytes) throws IOException {
        Path temporary=Files.createTempFile(target.getParent(),".health-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {Files.write(temporary,bytes);Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temporary);}
    }
    private static String cell(String raw) {
        String value=raw==null ? "" : raw;
        if(!value.isEmpty() && "=+-@\t\r\n".indexOf(value.charAt(0))>=0) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
