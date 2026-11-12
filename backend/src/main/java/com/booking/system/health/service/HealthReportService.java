package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthReportService {
    private static final int MAX_FILE_BYTES=20*1024*1024;
    private final HealthReportSnapshot snapshots;
    private final HealthAccess access;
    private final java.util.concurrent.Semaphore exportSlot=new java.util.concurrent.Semaphore(1);
    public Summary preview(Request request,HrImportActor actor){return snapshots.read(request,actor).summary();}
    // The snapshot transaction finishes before POI/PDFBox allocate or render a file.
    public byte[] export(Request request,boolean pdf,HrImportActor actor) {
        access.requireManager(actor);
        if(pdf&&request.employeeId()==null)throw HrApiException.badRequest("HEALTH_PDF_PERSON_REQUIRED","Chọn một người để xuất PDF cá nhân.");
        if(!exportSlot.tryAcquire())throw HrApiException.conflict("HEALTH_REPORT_BUSY","Đang tạo một báo cáo sức khỏe khác. Vui lòng thử lại sau; bộ lọc vẫn được giữ.");
        try {
            var snapshot=snapshots.read(request,actor);
            byte[] bytes=pdf?HealthReportPdf.render(snapshot):HealthReportExcel.render(snapshot);
            if(bytes.length>MAX_FILE_BYTES)throw HrApiException.badRequest("HEALTH_REPORT_FILE_LIMIT","Tệp vượt 20 MiB. Hãy thu hẹp bộ lọc.");
            return bytes;
        } catch(IOException exception) {
            log.warn("Health report rendering failed: format={}, errorType={}",pdf?"pdf":"xlsx",exception.getClass().getSimpleName());
            throw HrApiException.badRequest("HEALTH_REPORT_FAILED","Không tạo được báo cáo. Hãy thử lại; bộ lọc vẫn được giữ nguyên.");
        } finally {exportSlot.release();}
    }
}
