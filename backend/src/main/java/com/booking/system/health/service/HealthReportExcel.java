package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.api.HrHealthReportDtos.*;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Raw clinical values always remain STRING cells, including formula-like input and comparators. */
public final class HealthReportExcel {
    private HealthReportExcel() { }
    public static byte[] render(Snapshot data) throws IOException {
        try(var book=new HealthPrivateWorkbook();var output=new ByteArrayOutputStream()) {
            var scope=sheet(book,"Pham vi","Nội dung","Giá trị");var request=data.scope();
            row(scope,"Từ năm",request.fromYear());row(scope,"Đến năm",request.toYear());row(scope,"Phạm vi",request.employeeId()==null?"Tập thể theo bộ lọc, không giới hạn trang UI":"Một người");
            row(scope,"Từ khóa",request.keyword());row(scope,"Nhóm hiện tại",request.group()==null?"Tất cả":HealthReportStatistics.group(request.group().name()));row(scope,"Dữ liệu",request.data());row(scope,"Sắp xếp",request.sort());
            row(scope,"Lượt đã chọn",request.visitIds()==null||request.visitIds().isEmpty()?"Tất cả lượt trong khoảng năm":String.join(", ",request.visitIds()));
            for(var note:data.summary().notes())row(scope,"Lưu ý",note);
            var stats=sheet(book,"Thong ke","Chỉ số","Số lượng","Mẫu số","Cơ sở tính");
            row(stats,"Người trong bộ lọc",data.summary().people(),data.summary().people(),"Theo phạm vi trên sheet Pham vi");
            row(stats,"Tổng lượt",data.summary().visits(),data.summary().visits(),"Giữ riêng nhiều lượt cùng năm");
            row(stats,"Tổng kết quả",data.summary().results(),data.summary().results(),"Ô nguồn đã nhập");
            data.summary().counts().forEach(c->row(stats,c.label(),c.count(),c.denominator(),c.basis()));
            var people=sheet(book,"Nhan su","ID nội bộ","Mã nhân viên","Họ tên","Ngày sinh ISO","Nhóm hiện tại","Lượt trong phạm vi");
            var counts=data.visits().stream().collect(Collectors.groupingBy(VisitRow::employeeId,Collectors.counting()));
            data.people().forEach(p->row(people,p.employeeId(),p.code(),p.name(),p.birthDate(),HealthReportStatistics.group(p.currentGroup()==null?null:p.currentGroup().name()),counts.getOrDefault(p.employeeId(),0L)));
            Map<String,Person> persons=data.people().stream().collect(Collectors.toMap(Person::employeeId,Function.identity()));
            var visits=sheet(book,"Luot kham","ID lượt","Mã nhân viên","Họ tên","Năm","Ngày khám ISO","Đợt","Cơ sở","Phân loại raw","Nguồn","SID","Trạng thái","Số kết quả");
            data.visits().forEach(r->{var v=r.visit();var p=persons.get(r.employeeId());row(visits,v.id(),p.code(),p.name(),v.year(),v.examinedOn(),v.campaign(),v.provider(),v.classification(),v.namespace(),v.sid(),v.status(),v.resultCount());});
            var byVisit=data.visits().stream().collect(Collectors.toMap(v->v.visit().id(),Function.identity()));
            var results=sheet(book,"Ket qua","ID lượt","Mã nhân viên","Họ tên","Năm","SID","Mã chỉ tiêu","Chỉ tiêu","Nhóm","Giá trị gốc","Đơn vị","Phương pháp","Tham chiếu nguồn","Sheet","Dòng","Cột","Tiêu đề nguồn");
            data.results().forEach(v->{var visit=byVisit.get(v.visitId());var p=persons.get(visit.employeeId());row(results,v.visitId(),p.code(),p.name(),visit.visit().year(),visit.visit().sid(),v.code(),v.label(),v.category(),v.raw(),v.unit(),v.method(),v.reference(),v.sourceSheet(),v.sourceRow(),v.sourceColumn(),v.sourceLabel());});
            comparison(book,data);
            book.finishSheets();book.writeAvoidingTempFiles(output);return output.toByteArray();
        }
    }
    private static void comparison(Workbook book,Snapshot data) {
        var sheet=sheet(book,"So sanh","Mã nhân viên","Họ tên","Chỉ tiêu","Đơn vị","Phương pháp","Năm","Ngày khám ISO","SID","Giá trị gốc / thiếu","Tham chiếu","Sheet","Dòng","Cột","Chênh lệch cuối - đầu (chuỗi chính xác)","Giải thích");
        if(data.comparison().isEmpty()){row(sheet,"So sánh từng người có 2–6 lượt trong phạm vi. Với nhiều hơn 6 lượt, chọn cụ thể ở hồ sơ cá nhân; toàn bộ dữ liệu vẫn ở sheet Ket qua.");return;}
        var visits=data.visits().stream().collect(Collectors.toMap(v->v.visit().id(),Function.identity()));
        var people=data.people().stream().collect(Collectors.toMap(Person::employeeId,Function.identity()));
        for(var r:data.comparison())for(var cell:r.cells()) {
            var v=visits.get(cell.visitId());var p=people.get(v.employeeId());
            row(sheet,p.code(),p.name(),r.indicator().label(),r.indicator().unit(),r.indicator().method(),v.visit().year(),v.visit().examinedOn(),v.visit().sid(),cell.missing()?"Không có dữ liệu":value(cell.raw()),cell.reference(),cell.sourceSheet(),cell.sourceRow(),cell.sourceColumn(),r.difference()==null?"":r.difference().toPlainString(),r.note());
        }
    }
    private static Sheet sheet(Workbook book,String name,String... headers) {
        var sheet=book.createSheet(name);((HealthPrivateWorkbook)book).columns(name,headers.length);row(sheet,(Object[])headers);var font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());
        var style=book.createCellStyle();style.setFont(font);style.setFillForegroundColor(IndexedColors.TEAL.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for(var cell:sheet.getRow(0))cell.setCellStyle(style);
        for(int i=0;i<headers.length;i++)sheet.setColumnWidth(i,Math.min(60,Math.max(20,headers[i].length()+4))*256);
        return sheet;
    }
    private static void row(Sheet sheet,Object... values){var row=sheet.createRow(sheet.getPhysicalNumberOfRows()==0?0:sheet.getLastRowNum()+1);for(int i=0;i<values.length;i++){var cell=row.createCell(i);Object value=values[i];if(value instanceof Integer n)cell.setCellValue(n);else if(value instanceof Long n)cell.setCellValue(n);else cell.setCellValue(value==null?"":value.toString());}}
    private static String value(String text){return text==null?"":text;}
}
