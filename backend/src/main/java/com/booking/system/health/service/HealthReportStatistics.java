package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.api.HrHealthReportDtos.*;
import java.util.*;

public final class HealthReportStatistics {
    private HealthReportStatistics() { }
    public static Summary summarize(Request scope,List<Person> people,List<VisitRow> visits,List<Measurement> results) {
        var counts=new ArrayList<Count>();int withData=(int)visits.stream().map(VisitRow::employeeId).distinct().count();
        counts.add(new Count("Người có lượt đã nhập",withData,people.size(),"Người trong bộ lọc"));
        counts.add(new Count("Người chưa có lượt trong phạm vi",people.size()-withData,people.size(),"Không suy thành chưa khám"));
        for(int year=scope.fromYear();year<=scope.toYear();year++) {
            final int y=year;var rows=visits.stream().filter(v->v.visit().year()==y).toList();
            counts.add(new Count("Người có dữ liệu năm "+year,rows.stream().map(VisitRow::employeeId).distinct().count(),people.size(),"Người trong bộ lọc"));
            counts.add(new Count("Lượt khám năm "+year,rows.size(),visits.size(),"Tổng lượt trong phạm vi"));
        }
        var groups=new TreeMap<String,Long>();people.forEach(p->groups.merge(group(p.currentGroup()==null?null:p.currentGroup().name()),1L,Long::sum));
        groups.forEach((key,value)->counts.add(new Count("Nhóm hiện tại: "+key,value,people.size(),"Người trong bộ lọc, không phải nhóm lịch sử")));
        var classes=new TreeMap<String,Long>();visits.forEach(v->classes.merge(classification(v.visit().classification()),1L,Long::sum));
        classes.forEach((key,value)->counts.add(new Count("Phân loại: "+key,value,visits.size(),"Theo từng lượt, không gộp thành phân loại người")));
        counts.add(new Count("Lượt thiếu ngày khám",visits.stream().filter(v->v.visit().examinedOn()==null).count(),visits.size(),"Không dùng ngày nhập thay ngày khám"));
        counts.add(new Count("Kết quả thiếu đơn vị",results.stream().filter(v->blank(v.unit())).count(),results.size(),"Mức có dữ liệu, không kết luận thiếu gói khám"));
        counts.add(new Count("Kết quả thiếu phương pháp",results.stream().filter(v->blank(v.method())).count(),results.size(),"Mức có dữ liệu, không kết luận thiếu gói khám"));
        counts.add(new Count("Kết quả thiếu khoảng tham chiếu",results.stream().filter(v->blank(v.reference())).count(),results.size(),"Không tự đặt ngưỡng y khoa"));
        counts.add(new Count("Lượt chưa có snapshot đơn vị",visits.size(),visits.size(),"Chưa có dữ liệu đơn vị tại ngày khám; chưa thống kê theo đơn vị"));
        return new Summary(people.size(),withData,visits.size(),results.size(),List.copyOf(counts),List.of(
            "Bản nội bộ đang rà soát; không phải phiếu bệnh viện hoặc bản đã công bố cho nhân viên.",
            "Không có dữ liệu không đồng nghĩa chưa khám hoặc sức khỏe bình thường. Không thay ô thiếu bằng 0.",
            "Nhóm nhân sự là hiện tại. Chưa có snapshot đơn vị/nhóm lịch sử để thống kê theo lần khám.",
            "Độ đầy đủ chỉ đo trường đang có; chưa có danh mục xét nghiệm bắt buộc để kết luận đủ gói khám.",
            "So sánh chỉ tính chênh lệch số học với cùng chỉ tiêu/đơn vị/phương pháp đủ thứ tự; không chẩn đoán.",
            "Báo cáo chỉ lấy kết quả đã nhập, không gồm dòng staging đang chờ rà soát."));
    }
    public static String group(String group){return group==null?"Chưa xác định":switch(group){case "OFFICE"->"Văn phòng";case "GENERAL_LABOR"->"Lao động phổ thông";default->"Chưa xác định";};}
    private static String classification(String raw){if(blank(raw))return "Chưa có dữ liệu";var text=raw.strip().toUpperCase(Locale.ROOT);return Set.of("I","II","III","IV","V").contains(text)?text:"Cần rà soát (giữ nguyên ở danh sách lượt)";}
    private static boolean blank(String value){return value==null||value.isBlank();}
}
