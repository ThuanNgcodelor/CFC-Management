package com.booking.system.health.importer.baseline;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.importer.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.io.ByteArrayInputStream;
import java.util.*;
import static com.booking.system.health.importer.HealthImportSupport.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

@Component
@RequiredArgsConstructor
public class HealthBaselineReader {
    private static final Set<String> METADATA=Set.of("stt","code","don vi","don vi cong tac","gioi tinh","phone","so dien thoai","khoi","chuc vu","phong ban");
    private final HealthWorkbookParser parser;

    public Dataset read(List<WorkbookInput> inputs) {
        if(inputs.isEmpty() || inputs.size()>3 || inputs.stream().map(WorkbookInput::kind).distinct().count()!=inputs.size()) throw invalid("Chọn tối đa ba loại workbook lịch sử khác nhau.");
        var sources=new ArrayList<Source>();var labels=new LinkedHashMap<String,String>();
        for(var input:inputs) {
            var inspection=parser.inspect(input.content());
            for(var sheet:inspection.sheets()) {
                if(isTask(input.kind(),sheet)) {sources.add(taskSource(input,sheet,inspection.fileHash()));continue;}
                if(!isResult(input.kind(),sheet)) continue;
                var mapping=mapping(sheet,labels);
                var request=new StageRequest("baseline-preview",sheet.name(),sheet.headerRow(),1,"CFC_HISTORY",false,null,mapping,null);
                var parsed=parser.parse(input.content(),request);
                sources.add(new Source(key(input.kind(),sheet.name()),input.kind(),input.label(),inspection.fileHash(),sheet.name(),sheet.hidden(),sheet.headerRow(),false,mapping,parsed.rows(),List.of()));
            }
        }
        if(sources.stream().noneMatch(s -> !s.taskList())) throw invalid("Không có sheet kết quả phù hợp với các workbook lịch sử đã chọn.");
        return new Dataset(List.copyOf(sources),Map.copyOf(labels));
    }
    private static boolean isTask(Kind kind,SheetInfo sheet) {
        return kind==Kind.SUPPLEMENT_2024 && normalized(sheet.name()).equals("sheet1");
    }
    private static boolean isResult(Kind kind,SheetInfo sheet) {
        String name=normalized(sheet.name());
        return switch(kind) {
            case RESULTS_2025 -> sheet.resultSheet();
            case HISTORY_2023_2024 -> Set.of("tong hop","sheet5","sheet4","gian tiep","truc tiep").contains(name) && sheet.resultSheet();
            case SUPPLEMENT_2024 -> name.equals("ket qua sk") && sheet.resultSheet();
        };
    }
    private static Map<Integer,Mapping> mapping(SheetInfo sheet,Map<String,String> labels) {
        var maps=new LinkedHashMap<Integer,Mapping>();var resultByName=new HashMap<String,String>();String previous=null;
        for(var column:sheet.columns()) {
            String name=normalized(column.name());Field field=column.suggested();String indicator=null;
            if(field==Field.IGNORE && !METADATA.contains(name)) {
                
                int reference=name.indexOf("gioi han");
                if(reference>=0) {
                    String prefix=name.substring(0,reference).trim();indicator=prefix.isBlank() ? previous : resultByName.get(prefix);
                    if(indicator==null) throw invalid("Chưa ghép được cột khoảng tham chiếu tại "+sheet.name()+" / "+column.letter()+"; cần ánh xạ thủ công.");
                    field=Field.REFERENCE;
                } else {
                    String label=column.name().isBlank() ? "Chưa xác định — "+sheet.name()+" / "+column.letter() : clean(column.name());indicator="HIST_"+hash(label.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ")).substring(0,30);
                    labels.putIfAbsent(indicator,label);resultByName.put(name,indicator);previous=indicator;field=Field.RESULT;
                }
            }
            maps.put(column.index(),new Mapping(field,indicator,"",""));
        }
        return Map.copyOf(maps);
    }
    private Source taskSource(WorkbookInput input,SheetInfo info,String fileHash) {
        var tasks=new ArrayList<Map<String,String>>();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(input.content()))) {
            Sheet sheet=workbook.getSheet(info.name());var formatter=new DataFormatter(Locale.ROOT);formatter.setUseCachedValuesForFormulaCells(true);
            for(org.apache.poi.ss.usermodel.Row row:sheet) {
                if(row.getRowNum()+1<=info.headerRow()) continue;
                var values=new LinkedHashMap<String,String>();values.put("sourceRow",Integer.toString(row.getRowNum()+1));
                for(var column:info.columns()) {
                    String value=clean(formatter.formatCellValue(row.getCell(column.index())));
                    if(!value.isBlank()) values.put(column.letter()+" · "+column.name(),value);
                }
                if(values.size()>1) tasks.add(Map.copyOf(values));
            }
        } catch(java.io.IOException ex) {throw invalid("Không đọc được sheet công việc bổ sung.");}
        return new Source(key(input.kind(),info.name()),input.kind(),input.label(),fileHash,info.name(),info.hidden(),info.headerRow(),true,Map.of(),List.of(),tasks);
    }
    private static String key(Kind kind,String sheet) {return kind.name()+":"+sheet;}
}
