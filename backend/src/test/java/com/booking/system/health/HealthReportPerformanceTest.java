package com.booking.system.health;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.health.entity.HealthTypes.IndicatorType;
import com.booking.system.health.service.*;
import com.booking.system.hr.enums.HrWorkforceGroup;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HealthReportPerformanceTest {
    @Test void boundedMaximumWorkbookAndPersonalPdfRemainReadable() throws Exception {
        var people=new ArrayList<Person>();var visits=new ArrayList<VisitRow>();var values=new ArrayList<Measurement>();var comparison=new ArrayList<ComparisonRow>();
        for(int n=0;n<500;n++) {
            String id="fake-person-"+n;people.add(new Person(id,"FAKE"+n,"Nhân viên giả lập "+n,null,HrWorkforceGroup.OFFICE,4L,2026));
            for(int year=2023;year<=2026;year++) {
                String visit="fake-visit-"+n+"-"+year;visits.add(new VisitRow(id,new Visit(visit,year,"Đợt giả lập","Cơ sở giả lập",null,"REVIEW","FAKE","SID"+visit,10L)));
                for(int k=0;k<10;k++)values.add(new Measurement(visit,"key"+k,"indicator"+k,"FAKE"+k,"Chỉ tiêu giả lập "+k,"Nhóm giả lập",IndicatorType.TEXT,"Kết quả giả lập <0.5","","","Tham chiếu nguồn giả lập","Sheet giả lập",2,"D","Tiêu đề giả lập"));
            }
            var owned=visits.subList(visits.size()-4,visits.size()).stream().map(VisitRow::visit).toList();
            for(int k=0;k<10;k++){final String key="key"+k;comparison.add(new HealthRecordComparison().row(new IndicatorKey(key,"indicator"+k,"FAKE"+k,"Chỉ tiêu giả lập "+k,"Nhóm giả lập",IndicatorType.TEXT,"",""),owned,values.subList(values.size()-40,values.size()).stream().filter(v->v.key().equals(key)).toList()));}
        }
        var scope=new Request(null,2023,2026,null,null,"ALL","codeAsc",null);
        var snapshot=new Snapshot(scope,people,visits,values,comparison,HealthReportStatistics.summarize(scope,people,visits,values));
        var pools=java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==java.lang.management.MemoryType.HEAP).toList();
        pools.forEach(java.lang.management.MemoryPoolMXBean::resetPeakUsage);long start=System.nanoTime();
        byte[] bytes=HealthReportExcel.render(snapshot);long millis=(System.nanoTime()-start)/1_000_000;
        long peak=pools.stream().mapToLong(p->p.getPeakUsage().getUsed()).sum();
        assertThat(bytes.length).isLessThan(20*1024*1024);
        // Read the large artifact as XML events so validation does not inflate measured export heap.
        try(var archive=org.apache.poi.openxml4j.opc.OPCPackage.open(new java.io.ByteArrayInputStream(bytes))) {
            var reader=new org.apache.poi.xssf.eventusermodel.XSSFReader(archive);var sheets=(org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator)reader.getSheetsData();
            while(sheets.hasNext())try(var input=sheets.next()) {
                if(Set.of("Ket qua","So sanh").contains(sheets.getSheetName())) {
                    int[] rows={0};var parser=javax.xml.parsers.SAXParserFactory.newInstance();parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
                    parser.newSAXParser().parse(input,new org.xml.sax.helpers.DefaultHandler(){@Override public void startElement(String uri,String local,String name,org.xml.sax.Attributes attrs){if("row".equals(name))rows[0]++;}});
                    assertThat(rows[0]).isEqualTo(20001);
                }
            }
        }
        var personalValues=values.subList(0,40);var personalVisits=visits.subList(0,4);var personalPeople=people.subList(0,1);
        var personalScope=new Request(people.getFirst().employeeId(),2023,2026,null,null,"ALL","codeAsc",null);
        var personal=new Snapshot(personalScope,personalPeople,personalVisits,personalValues,comparison.subList(0,10),HealthReportStatistics.summarize(personalScope,personalPeople,personalVisits,personalValues));
        byte[] pdf=HealthReportPdf.render(personal);
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf)){assertThat(document.getNumberOfPages()).isGreaterThan(1);}
        System.out.printf("HEALTH_REPORT_FAKE_BENCH people=500 visits=2000 results=20000 comparisonCells=20000 xlsxMs=%d xlsxBytes=%d heapPoolPeakMiB=%d%n",millis,bytes.length,peak/1024/1024);
    }
}
