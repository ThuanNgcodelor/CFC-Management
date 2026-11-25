package com.booking.system.hr;

import com.booking.system.hr.service.*;
import com.booking.system.hr.entity.HrPayrollImportRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

public class HrLeadershipPayrollParserTest {
    static final String[] HEADERS={"STT","M Số","Họ và Tên","Lương cơ bản","30% \n bù \n đầu năm","TổngTiền lương","Tổng thu","Thuế TNCN","BHXH 10,5%","Bgiặt","HTKK","Đảng","quỹ ASXH","quỹ HC","Thu ĐP","NH chuyển"};
    public static XSSFWorkbook workbook(int count) {
        var book=new XSSFWorkbook();var sheet=book.createSheet("Ban điều hành");sheet.createRow(0).createCell(0).setCellValue("Lương tháng 10/2026");
        var header=sheet.createRow(1);for(int n=0;n<HEADERS.length;n++)header.createCell(n).setCellValue(HEADERS[n]);
        for(int n=0;n<count;n++) {
            var row=sheet.createRow(n+2);row.createCell(0).setCellValue(n+1);row.createCell(1).setCellValue("DEMO"+(n+1));row.createCell(2).setCellValue("Lãnh đạo giả lập "+(n+1));
            for(int c=3;c<16;c++)row.createCell(c).setCellValue(c==3?10000000:c==4?3000000:c==5?13000000:c==15?12000000:c==6?1000000:0);
        }
        var total=sheet.createRow(count+2);total.createCell(2).setCellValue("Tổng cộng");return book;
    }
    public static byte[] bytes(XSSFWorkbook book) throws Exception {try(book;var out=new ByteArrayOutputStream()){book.write(out);return out.toByteArray();}}
    final HrLeadershipPayrollParser parser=new HrLeadershipPayrollParser();
    @Test void readsFiveRowsAndIgnoresTotalWithoutRequiringWorkdaysOrAccount() throws Exception {
        var parsed=parser.parse(bytes(workbook(5)));assertThat(parsed.rows()).hasSize(5);assertThat(parsed.payrollMonth()).isEqualTo("10/2026");
        assertThat(parsed.rows().getFirst().values()).containsEntry("payrollType","LEADERSHIP");
        assertThat((BigDecimal)parsed.rows().getFirst().values().get("luongCoBan")).isEqualByComparingTo("10000000");
        assertThat(parsed.rows()).allSatisfy(r->assertThat(r.values()).doesNotContainKey("validationErrors"));
        assertThat(parser.parse(bytes(workbook(6))).rows()).hasSize(6);
    }
    @Test void acceptsCachedFormulaOrPlainNumbersWithoutFollowingExternalWorkbooks() throws Exception {
        var book=workbook(1);var cell=book.getSheetAt(0).getRow(2).getCell(5);cell.setCellFormula("D3+E3");book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
        assertThat((BigDecimal)parser.parse(bytes(book)).rows().getFirst().values().get("tienLuong")).isEqualByComparingTo("13000000");
    }
    @Test void errorsAndMissingCachedResultsRemainInvalidRatherThanZero() throws Exception {
        var book=workbook(2);book.getSheetAt(0).getRow(2).getCell(3).setCellErrorValue(FormulaError.REF.getCode());
        var uncached=book.getSheetAt(0).getRow(3).getCell(5);uncached.setCellFormula("D4+E4");uncached.getCTCell().unsetV();
        var parsed=parser.parse(bytes(book));
        assertThat(parsed.rows().getFirst().values().get("validationErrors").toString()).contains("D3");
        assertThat(parsed.rows().get(1).values().get("validationErrors").toString()).contains("F4");
        var row=new HrPayrollImportRow();row.setPayloadJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parsed.rows().getFirst().values()));
        assertThatThrownBy(()->HrPayrollPdfRenderer.render(row,"10/2026",true)).hasMessageContaining("ô lỗi");
        assertThatThrownBy(()->HrPayrollMessageRenderer.render(row,"10/2026")).hasMessageContaining("ô lỗi");
    }
    @Test void blankDeductionsAreZeroButBlankRequiredTotalsAreInvalid() throws Exception {
        var book=workbook(1);var row=book.getSheetAt(0).getRow(2);row.getCell(12).setBlank();row.getCell(15).setBlank();
        var value=parser.parse(bytes(book)).rows().getFirst().values();assertThat(value.get("asxh")).isEqualTo(BigDecimal.ZERO);assertThat(value.get("validationErrors").toString()).contains("P3");
    }
    @Test void rejectsDuplicateCodesAndWrongTemplateOrMonth() throws Exception {
        var book=workbook(2);book.getSheetAt(0).getRow(3).getCell(1).setCellValue("DEMO1");
        var duplicate=bytes(book);assertThatThrownBy(()->parser.parse(duplicate)).hasMessageContaining("bị lặp");
        book=workbook(1);book.getSheetAt(0).getRow(1).getCell(3).setCellValue("Khác");var wrong=bytes(book);assertThatThrownBy(()->parser.parse(wrong)).hasMessageContaining("mẫu lương lãnh đạo");
        book=workbook(1);book.getSheetAt(0).getRow(0).getCell(0).setCellValue("Lương tháng 13/2026");var month=bytes(book);assertThatThrownBy(()->parser.parse(month)).hasMessageContaining("Tháng lương");
    }
    @Test void preservesLeadershipFieldsInPdfAndCaptionWithoutInventingDaysOrAccount() throws Exception {
        var value=parser.parse(bytes(workbook(1))).rows().getFirst();var row=new HrPayrollImportRow();row.setEmployeeCode(value.employeeCode());row.setEmployeeName(value.employeeName());
        row.setPayloadJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value.values()));
        var pdf=HrPayrollPdfRenderer.render(row,"10/2026",false);
        try(var doc=Loader.loadPDF(pdf.bytes())) {assertThat(doc.getNumberOfPages()).isEqualTo(1);assertThat(new PDFTextStripper().getText(doc)).contains("Lương cơ bản","Bù đầu năm","Thuế TNCN","Quỹ ASXH","12.000.000 đ").doesNotContain("Số công","Số tài khoản");}
        assertThat(HrPayrollMessageRenderer.officialCaption(row,"10/2026")).contains("BAN ĐIỀU HÀNH","Thuế TNCN","12.000.000").doesNotContain("Số công","Số tài khoản");
    }
    @Test void inspectsOptionalPrivateSourceLocallyWithoutPersistingOrPrintingItsValues() throws Exception {
        String source=System.getenv("LEADERSHIP_PAYROLL_SOURCE");org.junit.jupiter.api.Assumptions.assumeTrue(source!=null&&!source.isBlank());
        var parsed=parser.parse(Files.readAllBytes(Path.of(source)));assertThat(parsed.rows()).hasSize(5);assertThat(parsed.payrollMonth()).isEqualTo("10/2026");
    }
}
