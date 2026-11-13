package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.hr.api.HrApiException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/** Generated personal report, never a download of a hospital workbook containing other people. */
public final class HealthReportPdf {
    public static final int MAX_RESULTS=1000,MAX_PAGES=200;
    private HealthReportPdf() { }
    public static byte[] render(Snapshot data) throws IOException {
        return render(data,"SỔ SỨC KHỎE — BÁO CÁO NỘI BỘ","Nội bộ, chưa công bố");
    }
    public static byte[] render(Snapshot data,String title,String footer) throws IOException {
        if(data.people().size()!=1||data.results().size()>MAX_RESULTS)throw invalid("PDF chỉ dành cho một người, tối đa 1.000 kết quả. Hãy thu hẹp khoảng năm hoặc chọn lượt khám.");
        try(var document=new PDDocument();var output=new ByteArrayOutputStream();var writer=new Pages(document,footer)) {
            var person=data.people().getFirst();
            writer.line(title,true);
            writer.line(person.code()+" · "+person.name(),true);
            writer.line("Ngày sinh: "+text(person.birthDate())+" · Khoảng năm: "+data.scope().fromYear()+"–"+data.scope().toYear(),false);
            writer.line("Phạm vi: "+data.visits().size()+" lượt / "+data.results().size()+" kết quả; không giới hạn trang giao diện.",false);
            for(var note:data.summary().notes())writer.line(note,false);
            writer.line("THỐNG KÊ TRONG PHẠM VI",true);
            for(var count:data.summary().counts())writer.line(count.label()+": "+count.count()+" / "+count.denominator()+" · "+count.basis(),false);
            var results=data.results().stream().collect(Collectors.groupingBy(Measurement::visitId));
            for(var row:data.visits()) {
                var v=row.visit();writer.line("LƯỢT KHÁM "+v.year()+" · SID "+text(v.sid()),true);
                writer.line(v.campaign()+" · "+v.provider()+" · Ngày khám: "+text(v.examinedOn()),false);
                writer.line("Nguồn: "+text(v.namespace())+" · Phân loại raw: "+text(v.classification()),false);
                for(var r:results.getOrDefault(v.id(),List.of())) {
                    writer.line(r.label()+" ["+r.code()+"]: "+text(r.raw()),true);
                    writer.line("Đơn vị: "+text(r.unit())+" · Phương pháp: "+text(r.method())+" · Tham chiếu: "+text(r.reference()),false);
                    writer.line("Nguồn ô: "+text(r.sourceSheet())+" / dòng "+text(r.sourceRow())+" / cột "+text(r.sourceColumn())+" · "+text(r.sourceLabel()),false);
                }
            }
            if(!data.comparison().isEmpty()) {
                writer.line("ĐẶT KẾT QUẢ CẠNH NHAU",true);
                for(var r:data.comparison()) {
                    writer.line(r.indicator().label()+" · "+text(r.indicator().unit())+" · "+text(r.indicator().method()),true);
                    for(int i=0;i<r.cells().size();i++){var v=data.visits().get(i).visit();var c=r.cells().get(i);writer.line(v.year()+" / SID "+text(v.sid())+": "+(c.missing()?"Không có dữ liệu":text(c.raw())),false);}
                    writer.line(r.note()+(r.difference()==null?"":" Chênh lệch cuối − đầu: "+r.difference().toPlainString()),false);
                }
            }
            writer.closePage();document.save(output);return output.toByteArray();
        }
    }
    private static String text(Object value){return value==null||value.toString().isBlank()?"Chưa có dữ liệu":value.toString();}
    private static HrApiException invalid(String message){return HrApiException.badRequest("HEALTH_PDF_SCOPE_INVALID",message);}
    private static final class Pages implements AutoCloseable {
        private final PDDocument document;private final PDFont regular,bold;private final String footer;private PDPageContentStream canvas;private float y;
        Pages(PDDocument document,String footer) throws IOException {this.document=document;this.footer=footer;regular=font(document,"Regular");bold=font(document,"SemiBold");}
        private static PDFont font(PDDocument document,String weight) throws IOException {
            try(var input=HealthReportPdf.class.getResourceAsStream("/hr/fonts/BeVietnamPro-"+weight+".ttf")){if(input==null)throw new IOException("Missing bundled PDF font");return PDType0Font.load(document,input);}
        }
        void line(String value,boolean heading) throws IOException {
            PDFont font=heading?bold:regular;float size=heading?10:9;
            for(String paragraph:value.replace('\t',' ').split("\\R",-1)) {
                var line=new StringBuilder();float width=0;
                for(int offset=0;offset<paragraph.length();) {
                    int cp=paragraph.codePointAt(offset);offset+=Character.charCount(cp);if(Character.isISOControl(cp))continue;
                    String glyph=new String(Character.toChars(cp));float next;
                    try{next=font.getStringWidth(glyph)/1000*size;}catch(IllegalArgumentException exception){throw invalid("Nguồn chứa ký tự chưa được font PDF hỗ trợ. Hãy xuất Excel để giữ nguyên nội dung.");}
                    if(width+next>495&&!line.isEmpty()){draw(line.toString(),font,size);line.setLength(0);width=0;}
                    line.append(glyph);width+=next;
                }
                draw(line.toString(),font,size);
            }
            y-=4;
        }
        private void draw(String value,PDFont font,float size) throws IOException {
            if(canvas==null||y<55)newPage();canvas.beginText();canvas.setFont(font,size);canvas.newLineAtOffset(50,y);canvas.showText(value);canvas.endText();y-=14;
        }
        private void newPage() throws IOException {
            closePage();if(document.getNumberOfPages()>=MAX_PAGES)throw invalid("PDF vượt 200 trang. Hãy chọn ít năm hoặc ít lượt hơn.");
            var page=new PDPage(PDRectangle.A4);document.addPage(page);canvas=new PDPageContentStream(document,page);canvas.setNonStrokingColor(new Color(25,45,55));y=790;
            canvas.beginText();canvas.setFont(regular,8);canvas.newLineAtOffset(50,30);canvas.showText(footer+" · Trang "+document.getNumberOfPages());canvas.endText();
        }
        void closePage() throws IOException {if(canvas!=null){canvas.close();canvas=null;}}
        @Override public void close() throws IOException {closePage();}
    }
}
