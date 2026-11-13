package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.entity.HealthTypes.IndicatorType;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class HealthRecordComparison {
    private static final int CHART_MAX_SIGNIFICANT_DIGITS=15;
    public ComparisonRow row(IndicatorKey key,List<Visit> visits,List<Measurement> values) {
        var matching=new HashMap<String,Measurement>();
        values.stream().filter(v->v.key().equals(key.key())).forEach(v->matching.put(v.visitId(),v));
        var cells=visits.stream().map(visit->cell(visit.id(),matching.get(visit.id()))).toList();
        boolean metadata=key.unit()!=null&&!key.unit().isBlank()&&key.method()!=null&&!key.method().isBlank();
        boolean ordered=visits.stream().collect(java.util.stream.Collectors.groupingBy(Visit::year)).values().stream()
            .allMatch(group->group.size()==1 || group.stream().allMatch(v->v.examinedOn()!=null) && group.stream().map(Visit::examinedOn).distinct().count()==group.size());
        boolean comparable=key.type()==IndicatorType.NUMBER&&metadata&&ordered&&cells.stream().allMatch(c->c.numeric()!=null);
        String note=comparable?"Cùng chỉ tiêu/đơn vị/phương pháp; chênh lệch là số học, không đánh giá tốt/xấu."
            :!metadata?"Cần đối chiếu đơn vị/phương pháp; chưa tính chênh lệch hoặc vẽ xu hướng."
            :!ordered?"Nhiều lượt cùng năm chưa đủ ngày để xác định thứ tự; chỉ đặt kết quả cạnh nhau."
            :"Có ô thiếu hoặc dữ liệu chưa đủ điều kiện biểu diễn số học an toàn; giữ giá trị gốc, không nối thành xu hướng.";
        return new ComparisonRow(key,cells,comparable,comparable?cells.getLast().numeric().subtract(cells.getFirst().numeric()):null,note);
    }
    private static ComparisonCell cell(String visitId,Measurement value){return value==null?new ComparisonCell(visitId,true,null,null,null,null,null,null)
        :new ComparisonCell(visitId,false,value.raw(),value.reference(),value.sourceSheet(),value.sourceRow(),value.sourceColumn(),number(value.raw()));}
    private static BigDecimal number(String value){if(value==null||value.length()>64||!value.strip().matches("[+-]?\\d+(?:\\.\\d+)?"))return null;var parsed=new BigDecimal(value.strip());return parsed.precision()>CHART_MAX_SIGNIFICANT_DIGITS?null:parsed;}
}
