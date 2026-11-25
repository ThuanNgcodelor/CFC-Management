package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class HrLeadershipPayrollParser {
    private static final int MAX_BYTES=15*1024*1024, MAX_ROWS=2000, MAX_SHEETS=20, MAX_COLUMNS=64;
    private static final Pattern MONTH=Pattern.compile("thang\\s*(\\d{1,2})\\s*[/\\-]\\s*(\\d{4})");
    private static final Map<String,String> HEADERS=Map.ofEntries(
        Map.entry("stt","stt"),Map.entry("mso","maNv"),Map.entry("maso","maNv"),Map.entry("manv","maNv"),
        Map.entry("hovaten","hoTen"),Map.entry("hoten","hoTen"),Map.entry("luongcoban","luongCoBan"),
        Map.entry("30budaunam","buDauNam"),Map.entry("tongtienluong","tienLuong"),Map.entry("tongthu","tongThu"),
        Map.entry("thuetncn","thueTncn"),Map.entry("bhxh105","bhxh"),Map.entry("bgiat","baoGiat"),Map.entry("htkk","htkk"),
        Map.entry("dang","thuDangPhi"),Map.entry("quyasxh","asxh"),Map.entry("quyhc","xhhc"),Map.entry("thudp","doanPhi"),Map.entry("nhchuyen","nganHangChuyen"));
    private static final Set<String> REQUIRED=new HashSet<>(HEADERS.values());
    private static final Set<String> REQUIRED_AMOUNTS=Set.of("luongCoBan","buDauNam","tienLuong","tongThu","nganHangChuyen");

    public HrPayrollWorkbookParser.ParsedWorkbook parse(byte[] bytes) {
        if(bytes==null||bytes.length==0)throw invalid("File lương lãnh đạo rỗng.");
        if(bytes.length>MAX_BYTES)throw invalid("File lương không được vượt quá 15 MB.");
        validateZip(bytes);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if(book.getNumberOfSheets()>MAX_SHEETS)throw invalid("File có quá nhiều sheet.");
            for(var sheet:book) for(int header=0;header<Math.min(30,sheet.getLastRowNum()+1);header++) {
                var columns=columns(sheet.getRow(header));
                if(columns.keySet().containsAll(REQUIRED))return parseSheet(bytes,sheet,header,columns);
            }
            throw invalid("Không tìm thấy mẫu lương lãnh đạo: cần STT, M Số, Họ và Tên, Lương cơ bản, 30% bù đầu năm, TổngTiền lương, Tổng thu và các cột khoản thu/NH chuyển trong 30 dòng đầu.");
        } catch(HrApiException error) {throw error;}
        catch(Exception error) {throw invalid("Không đọc được file lương lãnh đạo .xlsx. Hãy kiểm tra file không bị hỏng hoặc đặt mật khẩu.");}
    }
    private static void validateZip(byte[] bytes) {
        long total=0;int entries=0;boolean workbook=false;
        try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>256||entry.getName().contains("..")||entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject"))throw invalid("Cấu trúc file lương không được hỗ trợ.");
                workbook|=entry.getName().equals("xl/workbook.xml");int size;
                while((size=zip.read(buffer))!=-1)if((total+=size)>40L*1024*1024)throw invalid("Dữ liệu Excel giải nén vượt giới hạn 40 MB.");
            }
        } catch(HrApiException error){throw error;}
        catch(java.io.IOException error){throw invalid("File Excel .xlsx không hợp lệ.");}
        if(!workbook)throw invalid("Chỉ nhận workbook .xlsx không mã hóa.");
    }
    private HrPayrollWorkbookParser.ParsedWorkbook parseSheet(byte[] bytes,Sheet sheet,int header,Map<String,Integer> columns) throws Exception {
        if(sheet.getLastRowNum()>MAX_ROWS)throw invalid("File lương lãnh đạo vượt giới hạn 2.000 dòng.");
        String month=month(sheet,header);var rows=new ArrayList<HrPayrollWorkbookParser.PayrollRow>();var seen=new HashSet<String>();
        for(int n=header+1;n<=sheet.getLastRowNum();n++) {
            var source=sheet.getRow(n);if(source==null)continue;
            String code=text(source.getCell(columns.get("maNv"))).toUpperCase(Locale.ROOT), name=text(source.getCell(columns.get("hoTen")));
            if(key(name).contains("tongcong")||key(name).contains("congcong"))break;
            if(code.isBlank()&&name.isBlank())continue;
            var errors=new ArrayList<String>();
            if(!code.matches("[A-Z]+[0-9]+")||name.isBlank())errors.add("Thiếu hoặc sai mã nhân viên/họ tên tại dòng "+(n+1)+".");
            if(!code.isBlank()&&!seen.add(code))throw invalid("Mã nhân viên bị lặp trong file tại dòng "+(n+1)+".");
            var values=new LinkedHashMap<String,Object>();values.put("payrollType","LEADERSHIP");values.put("maNv",code);values.put("hoTen",name);
            for(var entry:columns.entrySet())if(!Set.of("stt","maNv","hoTen").contains(entry.getKey()))
                values.put(entry.getKey(),amount(source.getCell(entry.getValue()),entry.getKey(),n+1,errors));
            if(!errors.isEmpty())values.put("validationErrors",String.join(" ",errors));
            rows.add(new HrPayrollWorkbookParser.PayrollRow(n+1,code,name,"",values));
        }
        if(rows.isEmpty())throw invalid("Không tìm thấy dòng người nhận trong file lương lãnh đạo.");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new HrPayrollWorkbookParser.ParsedWorkbook(hash,bytes.length,sheet.getSheetName(),month,rows);
    }
    private static Object amount(Cell cell,String field,int row,List<String> errors) {
        String coordinate=cell==null?"dòng "+row:cell.getAddress().formatAsString();
        boolean formula=cell!=null&&cell.getCellType()==CellType.FORMULA;
        if(formula&&cell instanceof XSSFCell xml&&!xml.getCTCell().isSetV())return error(errors,coordinate,"Công thức chưa có kết quả lưu.");
        CellType type=cell==null?CellType.BLANK:formula?cell.getCachedFormulaResultType():cell.getCellType();
        if(type==CellType.ERROR)return error(errors,coordinate,"Ô Excel bị lỗi.");
        if(type==CellType.BLANK||(type==CellType.STRING&&cell.getStringCellValue().isBlank())) {
            if(formula||REQUIRED_AMOUNTS.contains(field))return error(errors,coordinate,"Thiếu số tiền.");
            return BigDecimal.ZERO;
        }
        try {
            if(type==CellType.NUMERIC) {
                String raw=cell instanceof XSSFCell xml?xml.getRawValue():Double.toString(cell.getNumericCellValue());
                return new BigDecimal(raw);
            }
            if(type==CellType.STRING) {
                String value=cell.getStringCellValue().trim().replace("₫","").replaceAll("\\s+","");
                if(value.matches("-?\\d+([.,]\\d{1,2})?"))return new BigDecimal(value.replace(',','.'));
                if(value.matches("-?\\d{1,3}([.,]\\d{3})+"))return new BigDecimal(value.replace(".","").replace(",",""));
            }
        } catch(NumberFormatException exception) {return error(errors,coordinate,"Số tiền không hợp lệ.");}
        return error(errors,coordinate,"Cần số tiền hoặc công thức đã lưu kết quả số.");
    }
    private static String error(List<String> errors,String cell,String detail) {errors.add(cell+": "+detail);return "Chưa có số hợp lệ";}
    private static Map<String,Integer> columns(Row row) {
        var result=new LinkedHashMap<String,Integer>();if(row==null)return result;
        for(int n=0;n<Math.min(row.getLastCellNum(),MAX_COLUMNS);n++) {
            String field=HEADERS.get(key(text(row.getCell(n))));
            if(field!=null&&result.putIfAbsent(field,n)!=null)throw invalid("Có cột tiêu đề trùng trong mẫu lương lãnh đạo.");
        }
        return result;
    }
    private static String month(Sheet sheet,int header) {
        for(int n=0;n<=header;n++) {var row=sheet.getRow(n);if(row==null)continue;
            for(int c=0;c<Math.min(row.getLastCellNum(),MAX_COLUMNS);c++) {
                var match=MONTH.matcher(normalized(text(row.getCell(c))));
                if(match.find()) {int month=Integer.parseInt(match.group(1));if(month<1||month>12)throw invalid("Tháng lương không hợp lệ.");return String.format(Locale.ROOT,"%02d/%s",month,match.group(2));}
            }
        }
        throw invalid("Không tìm thấy tháng/năm lương ở tiêu đề file lãnh đạo.");
    }
    private static String text(Cell cell) {return cell==null?"":new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();}
    private static String normalized(String text) {return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT);}
    private static String key(String text) {return normalized(text).replaceAll("[^a-z0-9]","");}
    private static HrApiException invalid(String message) {return HrApiException.badRequest("PAYROLL_LEADERSHIP_INVALID",message);}
}
