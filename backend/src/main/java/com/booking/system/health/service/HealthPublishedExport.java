package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthPublishedExport {
    private final HealthPublishedReader reader;
    private final java.util.concurrent.Semaphore slot=new java.util.concurrent.Semaphore(1);
    public byte[] export(String employee,String id,HrImportActor actor) {
        var content=reader.current(employee,id,actor);
        if(!slot.tryAcquire())throw HrApiException.conflict("HEALTH_REPORT_BUSY","Đang tạo một phiếu công bố khác. Hãy thử lại sau.");
        try {
            var body=content.snapshot();var year=body.visit().year();var scope=new Request(employee,year,year,null,null,"ALL","codeAsc",List.of(body.visit().id()));
            var people=List.of(body.person());var visits=List.of(new VisitRow(employee,body.visit()));var raw=HealthReportStatistics.summarize(scope,people,visits,body.results());
            var notes=new ArrayList<String>();notes.add("Phiên bản công bố "+content.info().revision()+" · "+content.info().createdAt()+" · Dữ liệu chưa đầy đủ.");notes.add("Lý do công bố: "+content.info().publishReason());notes.addAll(raw.notes().subList(1,raw.notes().size()));
            var data=new Snapshot(scope,people,visits,body.results(),List.of(),new Summary(raw.people(),raw.peopleWithData(),raw.visits(),raw.results(),raw.counts(),notes));
            byte[] bytes=HealthReportPdf.render(data,"SỔ SỨC KHỎE — PHIÊN BẢN CÔNG BỐ","Phiên bản "+content.info().revision()+" · Bản tổng hợp, không thay phiếu bệnh viện");
            reader.checkCurrent(employee,id,actor);return bytes;
        } catch(IOException exception){log.warn("Published health PDF failed: type={}",exception.getClass().getSimpleName());throw HrApiException.badRequest("HEALTH_PUBLISHED_PDF_FAILED","Không tạo được phiếu công bố; hãy thử lại.");}
        finally{slot.release();}
    }
}
