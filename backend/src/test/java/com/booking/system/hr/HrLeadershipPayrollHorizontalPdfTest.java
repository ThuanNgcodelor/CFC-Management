package com.booking.system.hr;

import com.booking.system.hr.entity.HrPayrollImportRow;
import com.booking.system.hr.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HrLeadershipPayrollHorizontalPdfTest {
    private HrPayrollImportRow fixture() throws Exception {
        var value=new HrLeadershipPayrollParser().parse(HrLeadershipPayrollParserTest.bytes(HrLeadershipPayrollParserTest.workbook(1))).rows().getFirst();
        var row=new HrPayrollImportRow();row.setEmployeeCode("DEMO1");row.setEmployeeName("Lãnh đạo giả lập");
        row.setPayloadJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value.values()));return row;
    }
    @Test void containsExactlyOneHorizontalRecipientRowWithAllColumnsInSourceOrder() throws Exception {
        var row=fixture();var pdf=HrPayrollPdfRenderer.render(row,"10/2026",false);
        try(var document=Loader.loadPDF(pdf.bytes())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            var box=document.getPage(0).getMediaBox();assertThat(box.getWidth()).isGreaterThan(box.getHeight()*4);
            var positions=new Positions();positions.getText(document);
            String[] headers={"M Số","Họ và Tên","Lương cơ bản","30%","TổngTiền lương","Tổng thu","Thuế TNCN","BHXH 10,5%","Bgiặt","HTKK","Đảng","quỹ ASXH","quỹ HC","Thu ĐP","NH chuyển"};
            float previous=-1;
            for(String label:headers) {
                var found=positions.labels.stream().filter(p->p.text().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing header "+label));
                assertThat(found.x()).isGreaterThan(previous);previous=found.x();
            }
            var strip=new PDFTextStripper();String text=strip.getText(document);
            assertThat(text).contains("DEMO1","Lãnh đạo giả lập","12.000.000","Đơn vị: đồng").doesNotContain("DEMO2","Số công","Số tài khoản","BẢN GỬI THỬ");
            var code=positions.labels.stream().filter(p->p.text().equals("DEMO1")).findFirst().orElseThrow();
            var net=positions.labels.stream().filter(p->p.text().equals("12.000.000")).findFirst().orElseThrow();
            assertThat(net.y()).isEqualTo(code.y());
            String preview=System.getProperty("payroll.horizontal.preview");
            if(preview!=null) {
                var output=Path.of(preview);Files.write(output,pdf.bytes());
                javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0,96),"png",output.resolveSibling("leadership-horizontal.png").toFile());
            }
        }
    }
    @Test void longNamesWrapWithoutLosingLettersAndTestCopyRemainsMarked() throws Exception {
        var row=fixture();String name="Nguyễn Người Kiểm Thử Có Họ Và Tên Rất Dài";row.setEmployeeName(name);
        try(var document=Loader.loadPDF(HrPayrollPdfRenderer.render(row,"10/2026",true).bytes())) {
            var text=new PDFTextStripper().getText(document).replaceAll("\\s+"," ");
            assertThat(text).contains(name,"BẢN GỬI THỬ","Bản thử - không dùng làm chứng từ thanh toán.");
        }
    }
    private record Label(String text,float x,float y) { }
    private static class Positions extends PDFTextStripper {
        final List<Label> labels=new ArrayList<>();
        Positions() throws java.io.IOException { }
        @Override protected void writeString(String text,List<TextPosition> positions) {
            if(!positions.isEmpty())labels.add(new Label(text,positions.getFirst().getXDirAdj(),positions.getFirst().getYDirAdj()));
        }
    }
}
