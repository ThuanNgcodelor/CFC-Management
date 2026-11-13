package com.booking.system.health.importer.baseline;

import com.booking.system.health.importer.HealthParsedData;
import com.booking.system.health.importer.HealthWorkbookParser;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

@Component
public class HealthBaselineAnalyzer {
    public Report analyze(Dataset dataset,List<Identity> employees) {
        var findings=new ArrayList<Finding>();var sheets=new ArrayList<SheetCount>();var bySid=new HashMap<String,List<Entry>>();
        var byCode=employees.stream().filter(e -> !clean(e.code()).isBlank()).collect(Collectors.groupingBy(e -> code(e.code())));
        int count=0,tasks=0,matched=0,suggested=0,unmatched=0;var matchedPeople=new HashSet<String>();
        for(var source:dataset.sources()) {
            tasks+=source.tasks().size();var years=new TreeMap<Integer,Long>();int codes=0;
            for(var row:source.rows()) {
                count++;if(!row.code().isBlank()) codes++;
                int year=suggestedYear(source.kind(),row);years.merge(year,1L,Long::sum);
                if(year==0) finding(findings,"YEAR_UNCONFIRMED",source,row,"Chưa xác định được năm khám; cần xác minh nguồn.",List.of());
                if(!row.sid().isBlank()) bySid.computeIfAbsent(row.sid(),key -> new ArrayList<>()).add(new Entry(source,row));
                else finding(findings,"SID_MISSING",source,row,"Thiếu SID lượt khám.",List.of());
                var exact=byCode.getOrDefault(code(row.code()),List.of()).stream().filter(e -> samePerson(row,e)).toList();
                if(exact.size()==1) {
                    matched++;matchedPeople.add(exact.getFirst().id());
                    if(!row.birth().isBlank() && clean(exact.getFirst().birth()).isBlank()) finding(findings,"BIRTH_NOT_VERIFIED",source,row,"Snapshot nhân sự chưa có ngày/năm sinh; mới đối chiếu mã và tên.",List.of());
                }
                else {
                    var candidates=employees.stream().filter(e -> samePerson(row,e)).map(Identity::id).distinct().limit(20).toList();
                    if(!candidates.isEmpty()) suggested++;else unmatched++;
                    finding(findings,candidates.isEmpty() ? "PERSON_UNMATCHED" : "PERSON_CONFIRMATION",source,row,
                        candidates.isEmpty() ? "Chưa ghép chắc chắn với nhân sự." : "Chỉ là gợi ý tên/ngày sinh; cần xác nhận liên kết.",candidates);
                }
                inspectValues(source,row,findings);
            }
            sheets.add(new SheetCount(source.key(),source.fileLabel(),source.sheet(),source.hidden(),source.rows().size(),source.tasks().size(),codes,Map.copyOf(years)));
        }
        int nameConflicts=0,valueConflicts=0;
        for(var entries:bySid.values()) {
            var anchor=entries.stream().filter(e -> normalized(e.source().sheet()).equals("tong hop")).findFirst().orElse(entries.getFirst());
            var anchorValues=anchor.row().values().stream().collect(Collectors.toMap(HealthParsedData.Value::indicatorId,Function.identity(),(a,b)->a));
            for(var entry:entries) if(entry!=anchor) {
                if(!clean(anchor.row().name()).equals(clean(entry.row().name()))) {
                    nameConflicts++;
                    finding(findings,normalized(anchor.row().name()).equals(normalized(entry.row().name())) ? "NAME_VARIANT" : "NAME_CONFLICT",entry.source(),entry.row(),"Cách ghi họ tên khác nguồn cùng SID; giữ nguồn và chờ xác nhận.",List.of());
                }
                if(!anchor.row().code().isBlank() && !entry.row().code().isBlank() && !code(anchor.row().code()).equals(code(entry.row().code()))) finding(findings,"CODE_CONFLICT",entry.source(),entry.row(),"Cùng SID nhưng mã nhân viên khác nhau.",List.of());
                for(var value:entry.row().values()) {
                    var old=anchorValues.get(value.indicatorId());
                    if(old!=null && !clean(old.rawValue()).equals(clean(value.rawValue()))) {valueConflicts++;finding(findings,"VALUE_CONFLICT",entry.source(),entry.row(),"Kết quả cùng chỉ tiêu/SID khác nguồn đối chiếu; không ghi đè tự động.",List.of());}
                }
            }
        }
        var kinds=findings.stream().collect(Collectors.groupingBy(Finding::kind,TreeMap::new,Collectors.counting()));
        return new Report(sheets,count,bySid.size(),tasks,matched,matchedPeople.size(),suggested,unmatched,nameConflicts,valueConflicts,Map.copyOf(kinds),List.copyOf(findings));
    }
    public static int suggestedYear(Kind kind,HealthParsedData.Row row) {
        if(row.date()!=null) return row.date().getYear();
        LocalDate hint=HealthWorkbookParser.sidDate(row.sid());
        if(hint!=null) return hint.getYear();
        return kind==Kind.RESULTS_2025 ? 2025 : kind==Kind.SUPPLEMENT_2024 ? 2024 : 0;
    }
    public static boolean samePerson(HealthParsedData.Row row,Identity employee) {
        if(!normalized(row.name()).equals(normalized(employee.name()))) return false;
        String left=clean(row.birth()),right=clean(employee.birth());
        if(left.isBlank() || right.isBlank()) return true;
        String a=year(left),b=year(right);
        if(a==null || b==null || !a.equals(b)) return false;
        return left.length()==4 || right.length()==4 || date(left).equals(date(right));
    }
    private static String year(String value) {LocalDate date=date(value);return date!=null ? Integer.toString(date.getYear()) : value.matches("\\d{4}") ? value : null;}
    private static LocalDate date(String value) {
        for(var format:List.of("uuuu-MM-dd","d/M/uuuu","d-M-uuuu")) try {return LocalDate.parse(value,java.time.format.DateTimeFormatter.ofPattern(format).withResolverStyle(java.time.format.ResolverStyle.STRICT));}
        catch(java.time.DateTimeException ex) { /* Only supported source precision participates in matching. */ }
        return null;
    }
    private static void inspectValues(Source source,HealthParsedData.Row row,List<Finding> findings) {
        for(var value:row.values()) if(value.label().isBlank()) finding(findings,"COLUMN_UNMAPPED",source,row,"Cột "+value.column()+" chưa có tiêu đề; giữ nguồn, cần ánh xạ thủ công.",List.of());
        for(String error:row.errors()) finding(findings,"SOURCE_ERROR",source,row,error,List.of());
        var classification=row.values().stream().filter(v -> HealthClassification.isClassification(v.label())).findFirst();
        if(classification.isEmpty()) finding(findings,"CLASSIFICATION_MISSING",source,row,"Nguồn chưa có phân loại sức khỏe; không suy là bình thường.",List.of());
        else if(HealthClassification.normalize(classification.get().rawValue())==null) finding(findings,"CLASSIFICATION_INVALID",source,row,"Phân loại ngoài I–V; giữ nguyên giá trị gốc và yêu cầu rà soát.",List.of());
    }
    private static void finding(List<Finding> out,String kind,Source source,HealthParsedData.Row row,String message,List<String> candidates) {
        out.add(new Finding(kind,source.key(),row.sourceRow(),row.sid(),row.code(),message,candidates));
    }
    private static String code(String value) {return clean(value).toUpperCase(Locale.ROOT);}
    private record Entry(Source source,HealthParsedData.Row row) { }
}
