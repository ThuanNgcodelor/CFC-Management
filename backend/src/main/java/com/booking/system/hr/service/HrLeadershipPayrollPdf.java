package com.booking.system.hr.service;

import com.booking.system.hr.entity.HrPayrollImportRow;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** A wide, single-recipient payslip; never pass a workbook or other employees to this renderer. */
final class HrLeadershipPayrollPdf {
    private static final float WIDTH=1660, HEIGHT=300, MARGIN=24, TOP=210, HEADER_HEIGHT=88, ROW_HEIGHT=52;
    private static final Color GREEN=new Color(183,238,193), TEAL=new Color(181,235,229), PALE=new Color(240,249,242);
    private record Column(String key,String title,float width) { }
    private static final List<Column> COLUMNS=List.of(
        new Column("maNv","M Số",54),new Column("hoTen","Họ và Tên",185),
        new Column("luongCoBan","Lương cơ bản",123),new Column("buDauNam","30%\nbù\nđầu năm",111),
        new Column("tienLuong","TổngTiền lương",125),new Column("tongThu","Tổng thu",111),
        new Column("thueTncn","Thuế TNCN",102),new Column("bhxh","BHXH 10,5%",112),
        new Column("baoGiat","Bgiặt",82),new Column("htkk","HTKK",84),new Column("thuDangPhi","Đảng",91),
        new Column("asxh","quỹ ASXH",85),new Column("xhhc","quỹ HC",74),new Column("doanPhi","Thu ĐP",90),
        new Column("nganHangChuyen","NH chuyển",132));
    private HrLeadershipPayrollPdf() { }

    static HrPayrollPdfRenderer.PayrollPdf render(HrPayrollImportRow row,String month,boolean testCopy,Map<String,Object> payload) throws Exception {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            var page=new PDPage(new PDRectangle(WIDTH,HEIGHT));document.addPage(page);
            var regular=HrPayrollPdfRenderer.loadFont(document,"/hr/fonts/BeVietnamPro-Regular.ttf");
            var bold=HrPayrollPdfRenderer.loadFont(document,"/hr/fonts/BeVietnamPro-SemiBold.ttf");
            try(var canvas=new PDPageContentStream(document,page)) {
                text(canvas,bold,22,MARGIN,HEIGHT-38,"PHIẾU LƯƠNG BAN ĐIỀU HÀNH");
                text(canvas,regular,12,MARGIN,HEIGHT-60,"Kỳ lương "+HrPayrollMessageRenderer.displayMonth(month)+" · Đơn vị: đồng");
                if(testCopy)text(canvas,bold,13,WIDTH-350,HEIGHT-38,"BẢN GỬI THỬ - KHÔNG CHÍNH THỨC");
                table(canvas,regular,bold,row,payload);
                text(canvas,regular,10,MARGIN,30,"Nếu có thắc mắc về phiếu lương, vui lòng liên hệ phòng Kế toán.");
                text(canvas,regular,9,MARGIN,14,testCopy?"Bản thử - không dùng làm chứng từ thanh toán.":"Tài liệu bảo mật - chỉ dành cho người nhận.");
            }
            document.save(output);
            return new HrPayrollPdfRenderer.PayrollPdf(output.toByteArray(),HrPayrollPdfRenderer.fileName(row,month));
        }
    }
    private static void table(PDPageContentStream canvas,PDFont regular,PDFont bold,HrPayrollImportRow row,Map<String,Object> payload) throws Exception {
        float total=(float)COLUMNS.stream().mapToDouble(Column::width).sum(),x=MARGIN;
        for(int i=0;i<COLUMNS.size();i++) {
            var column=COLUMNS.get(i);float width=column.width()*(WIDTH-2*MARGIN)/total;
            canvas.setNonStrokingColor(i>=9&&i<=12?TEAL:GREEN);
            canvas.addRect(x,TOP-HEADER_HEIGHT,width,HEADER_HEIGHT);canvas.fill();
            canvas.setNonStrokingColor(PALE);canvas.addRect(x,TOP-HEADER_HEIGHT-ROW_HEIGHT,width,ROW_HEIGHT);canvas.fill();
            cell(canvas,bold,column.title(),x,TOP,width,HEADER_HEIGHT,12,false);
            String value=switch(column.key()) {
                case "maNv" -> HrPayrollMessageRenderer.safe(row.getEmployeeCode());
                case "hoTen" -> HrPayrollMessageRenderer.safe(row.getEmployeeName());
                default -> HrPayrollMessageRenderer.formattedMoney(payload,column.key());
            };
            cell(canvas,i==COLUMNS.size()-1?bold:regular,value,x,TOP-HEADER_HEIGHT,width,ROW_HEIGHT,12,i>=2);
            canvas.setStrokingColor(Color.BLACK);canvas.setLineWidth(.65f);
            canvas.addRect(x,TOP-HEADER_HEIGHT-ROW_HEIGHT,width,HEADER_HEIGHT+ROW_HEIGHT);canvas.stroke();
            canvas.moveTo(x,TOP-HEADER_HEIGHT);canvas.lineTo(x+width,TOP-HEADER_HEIGHT);canvas.stroke();x+=width;
        }
    }
    private static void cell(PDPageContentStream canvas,PDFont font,String value,float x,float top,float width,float height,float preferred,boolean right) throws Exception {
        float size=preferred;List<String> lines;
        while(true) {
            lines=right?List.of(value):wrap(font,value,size,width-12);
            boolean fits=lines.size()*(size+4)<=height-8;
            for(String line:lines)if(measure(font,line,size)>width-12)fits=false;
            // Numbers must stay complete on one line; names can wrap instead of being truncated.
            if(right)fits=measure(font,value,size)<=width-12;
            if(fits)break;
            if(size<=7)throw new IllegalArgumentException("Nội dung vượt khung phiếu lương ngang.");
            size-=.5f;
        }
        float baseline=top-(height-lines.size()*(size+4))/2-size;
        for(String line:lines) {
            float offset=right?width-6-measure(font,line,size):(width-measure(font,line,size))/2;
            text(canvas,font,size,x+offset,baseline,line);baseline-=size+4;
        }
    }
    private static List<String> wrap(PDFont font,String value,float size,float width) {
        var lines=new ArrayList<String>();
        for(String paragraph:value.split("\n",-1)) {
            var line=new StringBuilder();
            for(String word:paragraph.split(" +")) {
                String candidate=line.isEmpty()?word:line+" "+word;
                if(!line.isEmpty()&&measure(font,candidate,size)>width){lines.add(line.toString());line.setLength(0);}
                if(!line.isEmpty())line.append(' ');line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }
    private static float measure(PDFont font,String value,float size) {
        try{return font.getStringWidth(value)*size/1000;}
        catch(java.io.IOException error){throw new IllegalArgumentException("Không đo được chữ trong phiếu lương.",error);}
    }
    private static void text(PDPageContentStream canvas,PDFont font,float size,float x,float y,String value) throws Exception {
        canvas.setNonStrokingColor(Color.BLACK);canvas.beginText();canvas.setFont(font,size);canvas.newLineAtOffset(x,y);canvas.showText(value);canvas.endText();
    }
}
