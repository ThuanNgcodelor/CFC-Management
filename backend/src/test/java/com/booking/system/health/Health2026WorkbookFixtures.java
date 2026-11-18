package com.booking.system.health;

import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Synthetic hospital files only; no production employee or medical data is read. */
public final class Health2026WorkbookFixtures {
    private Health2026WorkbookFixtures() { }
    public record Entry(String code,String name,String sid,String date,String ast,String extra) { }
    public static Entry entry(int year,String code,String ast,String extra) {
        return new Entry(code,code.equals("HEALTHDEV01")?"Nhân viên giả lập":"Công nhân giả lập", "QA-"+year+"-"+code,year+"-09-01",ast,extra);
    }
    public static byte[] workbook(boolean expanded,List<Entry> entries) throws Exception {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Kết quả");
            int header=expanded?2:0;
            if(expanded)sheet.createRow(0).createCell(0).setCellValue("KẾT QUẢ GIẢ LẬP — KHÔNG PHẢI HỒ SƠ THẬT");
            String[] labels=expanded?new String[]{"Mã nhân viên","Chỉ tiêu bổ sung giả lập","Họ tên","SID","Ngày khám","AST (máy A)","AST - Giới hạn b/t"}
                :new String[]{"Họ tên","Mã nhân viên","SID","Ngày khám","AST (máy A)","AST - Giới hạn b/t"};
            var titles=sheet.createRow(header);for(int i=0;i<labels.length;i++)titles.createCell(i).setCellValue(labels[i]);
            if(expanded)sheet.createRow(header+1).createCell(5).setCellValue("máy A");
            for(int i=0;i<entries.size();i++) {
                var e=entries.get(i);String[] values=expanded?new String[]{e.code(),e.extra(),e.name(),e.sid(),e.date(),e.ast(),"<34"}
                    :new String[]{e.name(),e.code(),e.sid(),e.date(),e.ast(),"<34"};
                var row=sheet.createRow(header+(expanded?2:1)+i);
                for(int c=0;c<values.length;c++)row.createCell(c).setCellValue(values[c]);
            }
            workbook.write(out);return out.toByteArray();
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Chọn thư mục /tmp/cfc-health-2026-* riêng cho file giả.");
        var folder=Path.of(args[0]).toAbsolutePath().normalize();
        if(!folder.startsWith(Path.of("/tmp"))||!folder.getFileName().toString().startsWith("cfc-health-2026-"))throw new IllegalArgumentException("Chỉ xuất vào thư mục QA riêng trong /tmp.");
        Files.createDirectories(folder);Files.setPosixFilePermissions(folder,PosixFilePermissions.fromString("rwx------"));
        var files=new LinkedHashMap<String,byte[]>();
        files.put("01-lich-su-gia-2024.xlsx",workbook(false,List.of(entry(2024,"HEALTHDEV01","20",""))));
        files.put("02-lich-su-gia-2025.xlsx",workbook(false,List.of(entry(2025,"HEALTHDEV01","22",""))));
        files.put("03-mau-moi-gia-2026.xlsx",workbook(true,List.of(entry(2026,"HEALTHDEV01","24","<0.5"),entry(2026,"HEALTHDEV02","25",""))));
        files.put("04-bo-sung-gia-2026.xlsx",workbook(true,List.of(entry(2026,"HEALTHDEV01","26","<0.5"))));
        files.put("05-du-lieu-loi-gia-2026.xlsx",workbook(true,List.of(entry(2026,"KHONG-TON-TAI","24",""),new Entry("HEALTHDEV02","","QA-2026-LOI","31/02/2026","25",""))));
        for(var file:files.entrySet()){var path=folder.resolve(file.getKey());Files.write(path,file.getValue());Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));}
        System.out.println("HEALTH_2026_SYNTHETIC_FILES_OK files="+files.size()+" folder="+folder);
    }
}
