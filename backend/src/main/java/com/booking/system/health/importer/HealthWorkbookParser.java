package com.booking.system.health.importer;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.importer.HealthParsedData.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.zip.ZipInputStream;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Component
public class HealthWorkbookParser {
    private static final int MAX_SHEETS = 30, MAX_ZIP_ENTRIES = 2000;
    public Inspection inspect(byte[] bytes) { return inspect(bytes, null, null, 1); }
    public Inspection inspect(byte[] bytes, String selectedSheet, Integer headerRow, int headerRows) {
        if (headerRows < 1 || headerRows > 3 || (headerRow != null && (headerRow < 1 || headerRow > 100))) throw invalid("Tiêu đề cần hàng bắt đầu 1–100 và từ 1–3 hàng.");
        validateZip(bytes);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheets = new ArrayList<SheetInfo>();
            if (selectedSheet != null && !selectedSheet.isBlank() && workbook.getSheet(selectedSheet) == null) throw invalid("Không tìm thấy sheet đã chọn.");
            if (workbook.getNumberOfSheets() > MAX_SHEETS) throw invalid("File có quá nhiều sheet.");
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                boolean selected = sheet.getSheetName().equals(selectedSheet);
                int header = selected && headerRow != null ? headerRow : detectHeader(sheet);
                int count = selected ? headerRows : 1;
                var columns = columns(sheet, header, count);
                boolean results = columns.stream().anyMatch(c -> c.suggested() == Field.NAME)
                        && columns.stream().anyMatch(c -> c.suggested() == Field.SID || c.suggested() == Field.RESULT || medical(c.name()));
                int rows = (int) rowNumbers(sheet, header + count - 1).stream().filter(r -> r > header).count();
                sheets.add(new SheetInfo(sheet.getSheetName(), workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index), header, rows, results, hash(json(columns.stream().map(c -> List.of(c.index(),c.name())).toList())), columns));
            }
            return new Inspection(hash(bytes), sheets);
        } catch (com.booking.system.hr.api.HrApiException ex) { throw ex; }
        catch (Exception ex) { throw invalid("Không đọc được workbook .xlsx. Hãy lưu lại file Excel và thử lại."); }
    }
    public Parsed parse(byte[] bytes, StageRequest request) {
        validateZip(bytes);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(request.sheet());
            if (sheet == null) throw invalid("Không tìm thấy sheet đã chọn.");
            var columns = columns(sheet, request.headerRow(), request.headerRows());
            validateMapping(columns, request);
            var rows = new ArrayList<HealthParsedData.Row>();
            for (int number : rowNumbers(sheet, request.headerRow() + request.headerRows() - 1)) {
                rows.add(parseRow(sheet.getRow(number - 1), columns, request));
            }
            if (rows.isEmpty()) throw invalid("Sheet không có dòng kết quả sau tiêu đề.");
            String fingerprint = hash(json(columns.stream().map(c -> List.of(c.index(), c.name())).toList()));
            return new Parsed(hash(bytes), fingerprint, request, List.copyOf(rows));
        } catch (com.booking.system.hr.api.HrApiException ex) { throw ex; }
        catch (Exception ex) { throw invalid("Không thể đọc dữ liệu sheet theo cấu hình đã chọn."); }
    }
    private static void validateZip(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw invalid("File Excel phải có dung lượng từ 1 byte đến 10 MB.");
        long total = 0; int entries = 0; boolean workbook = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES || entry.getName().contains("..") || entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject")) throw invalid("Cấu trúc Excel không được hỗ trợ.");
                workbook |= entry.getName().equals("xl/workbook.xml");
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ZIP_BYTES) throw invalid("Dữ liệu giải nén Excel vượt giới hạn 64 MB.");
                }
            }
        } catch (com.booking.system.hr.api.HrApiException ex) { throw ex; }
        catch (Exception ex) { throw invalid("File không phải workbook .xlsx hợp lệ hoặc đã mã hóa."); }
        if (!workbook) throw invalid("Chỉ hỗ trợ workbook .xlsx không mã hóa.");
    }
    private static int detectHeader(Sheet sheet) {
        int best = 1, score = 0;
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            if (row.getRowNum() >= 100) break;
            int candidate = 0;
            for (org.apache.poi.ss.usermodel.Cell cell : row) {
                Field field = suggest(text(cell));
                if (field == Field.NAME) candidate += 5;
                if (field == Field.SID || field == Field.EMPLOYEE_CODE) candidate += 3;
                if (field == Field.BIRTH) candidate++;
            }
            if (candidate > score) { score = candidate; best = row.getRowNum() + 1; }
        }
        return best;
    }
    private static List<Integer> rowNumbers(Sheet sheet, int afterHeader) {
        var rows = new ArrayList<Integer>(); int cells = 0;
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            boolean filled = false;
            for (org.apache.poi.ss.usermodel.Cell cell : row) {
                if (!text(cell).isBlank()) { filled = true; if (++cells > MAX_CELLS) throw invalid("File có quá nhiều ô dữ liệu."); }
            }
            if (filled && row.getRowNum() + 1 > afterHeader) rows.add(row.getRowNum() + 1);
            if (rows.size() > MAX_ROWS) throw invalid("Mỗi sheet tối đa 2.500 dòng kết quả.");
        }
        return rows;
    }
    private static List<Column> columns(Sheet sheet, int header, int count) {
        var used = new TreeSet<Integer>(); int cells = 0;
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            if (row.getRowNum() + 1 < header) continue;
            for (org.apache.poi.ss.usermodel.Cell cell : row) if (!text(cell).isBlank()) {
                used.add(cell.getColumnIndex());
                if (++cells > MAX_CELLS || used.size() > MAX_COLUMNS) throw invalid("Vượt giới hạn 256 cột hoặc 200.000 ô dữ liệu.");
            }
        }
        return used.stream().map(index -> {
            var names = new LinkedHashSet<String>();
            for (int offset = 0; offset < count; offset++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(header - 1 + offset);
                if (row != null && !text(row.getCell(index)).isBlank()) names.add(text(row.getCell(index)));
            }
            String name = String.join(" / ", names);
            return new Column(index, CellReference.convertNumToColString(index), name, suggest(name));
        }).toList();
    }
    private static void validateMapping(List<Column> columns, StageRequest request) {
        if (!request.columns().keySet().equals(columns.stream().map(Column::index).collect(java.util.stream.Collectors.toSet()))) throw invalid("Hãy ánh xạ hoặc chọn bỏ qua cho tất cả cột đang có dữ liệu.");
        var used = new HashSet<String>(); boolean name = false, result = false;
        for (var column : columns) {
            Mapping mapping = request.columns().get(column.index());
            if (mapping.field() == Field.IGNORE) continue;
            String key = mapping.field().name();
            if (mapping.field() == Field.RESULT || mapping.field() == Field.REFERENCE) {
                if (mapping.indicatorId() == null || mapping.indicatorId().isBlank()) throw invalid("Cột kết quả và khoảng tham chiếu phải chọn chỉ tiêu.");
                key += valueKey(mapping.indicatorId(), mapping.unit(), mapping.method());
            }
            if (!used.add(key)) throw invalid("Các cột bị ánh xạ trùng trường/chỉ tiêu; cần phân biệt phương pháp hoặc bỏ qua cột lặp.");
            name |= mapping.field() == Field.NAME; result |= mapping.field() == Field.RESULT;
        }
        for (String key : used) if (key.startsWith(Field.REFERENCE.name()) && !used.contains(Field.RESULT.name() + key.substring(Field.REFERENCE.name().length()))) throw invalid("Khoảng tham chiếu cần cột kết quả cùng chỉ tiêu, đơn vị và phương pháp.");
        if (!name || !result) throw invalid("Cần cột họ tên và ít nhất một cột kết quả. Sheet thống kê/công việc không phải kết quả khám.");
        if (columns.stream().anyMatch(c -> normalized(c.name()).equals("thong tin thieu"))) throw invalid("Đây là danh sách cần bổ sung thông tin, không phải bảng kết quả khám.");
    }
    private static HealthParsedData.Row parseRow(org.apache.poi.ss.usermodel.Row row, List<Column> columns, StageRequest request) {
        var fields = new EnumMap<Field,String>(Field.class); var errors = new ArrayList<String>(); var warnings = new ArrayList<String>();
        var refs = new HashMap<String,String>(); var values = new ArrayList<Value>();
        for (Column column : columns) {
            Mapping map = request.columns().get(column.index()); String value = text(row.getCell(column.index()));
            if (value.equals("#EXCEL_ERROR") && map.field()!=Field.IGNORE) errors.add("Ô "+column.letter()+" có lỗi Excel hoặc công thức chưa có kết quả lưu sẵn.");
            if (value.length() > MAX_CELL_CHARS) { errors.add("Ô " + column.letter() + " vượt 16.000 ký tự."); continue; }
            if (map.field() == Field.REFERENCE) refs.put(valueKey(map.indicatorId(),map.unit(),map.method()), value);
            else if (map.field() != Field.IGNORE && map.field() != Field.RESULT) fields.put(map.field(), value);
        }
        for (Column column : columns) {
            Mapping map = request.columns().get(column.index()); String value = text(row.getCell(column.index()));
            if (map.field() == Field.RESULT && !value.isBlank() && value.length() <= MAX_CELL_CHARS) {
                String ref = refs.getOrDefault(valueKey(map.indicatorId(),map.unit(),map.method()), referenceInHeader(column.name()));
                if (ref.length() > 500 || column.name().length() > 500) errors.add("Tên/khoảng tham chiếu quá dài ở cột " + column.letter() + ".");
                values.add(new Value(map.indicatorId(), value, ref, clean(map.unit()), clean(map.method()), column.letter(), column.name()));
            }
        }
        String name = fields.getOrDefault(Field.NAME, ""), code = fields.getOrDefault(Field.EMPLOYEE_CODE, "").toUpperCase(Locale.ROOT);
        String sid = fields.getOrDefault(Field.SID, "");
        if (name.isBlank()) errors.add("Thiếu họ tên.");
        if (name.length() > 255 || code.length() > 32 || sid.length() > 100) errors.add("Thông tin định danh vượt độ dài cho phép.");
        LocalDate date = date(fields.get(Field.EXAM_DATE), errors);
        if (date == null && request.dateFromSidConfirmed()) date = sidDate(sid);
        if (date == null) date = request.defaultDate();
        if (!request.dateFromSidConfirmed() && sidDate(sid) != null) warnings.add("Ngày từ tiền tố SID chưa được xác minh.");
        if (values.isEmpty()) errors.add("Không có kết quả trong các cột đã chọn.");
        String birth = fields.getOrDefault(Field.BIRTH, "");
        if (birth.length() > 32) errors.add("Ngày/năm sinh không hợp lệ.");
        return new HealthParsedData.Row(row.getRowNum()+1, code, name, birth, sid, date, List.copyOf(values), errors, warnings);
    }
    public static LocalDate sidDate(String sid) {
        if (sid == null || !sid.matches("\\d{6}-.*")) return null;
        try { return LocalDate.parse(sid.substring(0,6), DateTimeFormatter.ofPattern("ddMMuu").withResolverStyle(ResolverStyle.STRICT)); }
        catch (java.time.DateTimeException ex) { return null; }
    }
    private static LocalDate date(String value, List<String> errors) {
        if (value == null || value.isBlank()) return null;
        for (String format : List.of("uuuu-MM-dd", "d/M/uuuu", "d-M-uuuu")) {
            try { return LocalDate.parse(value, DateTimeFormatter.ofPattern(format).withResolverStyle(ResolverStyle.STRICT)); }
            catch (java.time.DateTimeException ex) { /* Try the next documented date format. */ }
        }
        errors.add("Ngày khám phải là ngày Excel hoặc dd/MM/yyyy."); return null;
    }
    private static String referenceInHeader(String name) {
        var matcher = java.util.regex.Pattern.compile("\\(([^)]*(?:[<>]|\\d\\s*-\\s*\\d)[^)]*)\\)").matcher(name);
        String value = ""; while (matcher.find()) value = matcher.group(1); return value;
    }
    private static boolean medical(String text) { return normalized(text).matches(".*(kham|ast|alt|ure|creatinin|sieu am|huyet ap|can nang|phan loai).*" ); }
    public static Field suggest(String name) {
        String key = normalized(name);
        if (List.of("ho ten","ho va ten").contains(key)) return Field.NAME;
        if (List.of("ma nhan vien","ma nv","ma so","ms").contains(key)) return Field.EMPLOYEE_CODE;
        if (key.equals("sid")) return Field.SID;
        if (List.of("nam sinh","ngay sinh").contains(key)) return Field.BIRTH;
        if (List.of("ngay kham","ngay lay mau").contains(key)) return Field.EXAM_DATE;
        return Field.IGNORE;
    }
    private static String text(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA && cell instanceof org.apache.poi.xssf.usermodel.XSSFCell x && !x.getCTCell().isSetV()) return "#EXCEL_ERROR";
        if (cell.getCellType() == CellType.ERROR || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.ERROR)) return "#EXCEL_ERROR";
        if ((cell.getCellType() == CellType.NUMERIC || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC)) && DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        DataFormatter formatter = new DataFormatter(Locale.ROOT); formatter.setUseCachedValuesForFormulaCells(true);
        return clean(formatter.formatCellValue(cell));
    }
}
