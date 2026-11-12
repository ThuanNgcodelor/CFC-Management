package com.booking.system.health.importer;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.importer.HealthImportContext.State;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Component
public class HealthImportPreview {
    public PreviewRow row(HealthImport batch, HealthImportRow staged, State context, int year) {
        if (staged.getPayload()==null) return new PreviewRow(staged.getId(),staged.getSourceRow(),"Đã hủy","","",null,staged.getState(),null,List.of(),List.of(),List.of(),List.of(),"",staged.getRowVersion());
        var source = read(staged.getPayload(),HealthParsedData.Row.class);
        var errors = new ArrayList<>(source.errors()); var warnings = new ArrayList<>(source.warnings());
        String employeeId = HealthImportContext.match(staged,source,context.employees());
        var matchedPerson=context.employees().get(employeeId);
        if(matchedPerson!=null && matchedPerson.getDateOfBirth()==null && !source.birth().isBlank()) warnings.add("Chưa đối chiếu ngày/năm sinh: dữ liệu nhân sự đang đối chiếu chưa có ngày sinh.");
        if (employeeId==null) errors.add("Chưa ghép chắc chắn với nhân sự. Hãy tìm và xác nhận người phù hợp.");
        if (source.date()!=null && source.date().getYear()!=year) errors.add("Ngày khám không thuộc năm của đợt khám đã chọn.");
        if (source.date()==null && HealthWorkbookParser.sidDate(source.sid())!=null && HealthWorkbookParser.sidDate(source.sid()).getYear()!=year) errors.add("Tiền tố SID khác năm đợt khám; cần xác minh ngày/năm trước khi nhập.");
        if (source.sid().isBlank() && !read(batch.getMappingJson(),StageRequest.class).noSidDateKeyConfirmed()) errors.add("Thiếu SID: chưa xác nhận khóa người/ngày; nếu có nhiều lượt cùng ngày, cần mã lượt riêng trong file.");
        String key = HealthImportContext.visitKey(source,employeeId,batch.getCampaignId());
        if (key==null) errors.add("Thiếu SID: cần mã nhân viên đã ghép và ngày khám được xác nhận.");
        var visit = key==null ? null : context.visits().get(key);
        var subject = employeeId==null ? null : context.subjects().get(employeeId);
        if (visit!=null && (subject==null || !visit.getSubjectId().equals(subject.getId()) || !visit.getCampaignId().equals(batch.getCampaignId()))) errors.add("SID đã thuộc người hoặc đợt khám khác; không thể ghi đè.");
        if (visit!=null && !Objects.equals(visit.getExaminedOn(),source.date())) errors.add("Ngày khám của SID không khớp lượt đang lưu; cần xác minh nguồn.");
        var cells = new ArrayList<Cell>(); var versions = new ArrayList<String>();
        for (var value : source.values()) {
            var indicator = context.indicators().get(value.indicatorId());
            if (indicator==null || indicator.getStatus()!=HealthTypes.IndicatorStatus.ACTIVE) { errors.add("Chỉ tiêu đã bị gỡ hoặc ngừng sử dụng."); continue; }
            if (com.booking.system.health.importer.baseline.HealthClassification.isClassification(value.label())) {
                String classification=com.booking.system.health.importer.baseline.HealthClassification.normalize(value.rawValue());
                if(classification==null) errors.add("Phân loại sức khỏe ngoài I–V; cần đính chính có lý do, giữ nguyên nguồn gốc.");
                else if(!classification.equals(value.rawValue())) warnings.add("Phân loại hiển thị chuẩn: "+classification+"; vẫn giữ giá trị gốc.");
            }
            String cellKey = valueKey(value.indicatorId(),value.unit(),value.method());
            var existing = visit==null ? null : context.results().get(visit.getId()+":"+cellKey);
            String action = existing==null ? "ADD" : same(existing,value) ? "UNCHANGED" : "CONFLICT";
            cells.add(new Cell(cellKey,value.indicatorId(),indicator.getName(),value.rawValue(),existing==null ? null : existing.getRawValue(),
                    value.reference(),existing==null ? null : existing.getReferenceText(),value.unit(),value.method(),action));
            versions.add(indicator.getId()+":"+indicator.getRowVersion()+":"+(existing==null ? "NEW" : existing.getId()+":"+existing.getRowVersion()));
        }
        var candidates = context.employees().values().stream().filter(e -> e.getEmployeeCode().equalsIgnoreCase(source.code())
                || normalized(e.getFullName()).equals(normalized(source.name())) || e.getId().equals(staged.getEmployeeId()))
                .sorted(Comparator.comparing(com.booking.system.hr.entity.HrEmployee::getEmployeeCode)).limit(10)
                .map(e -> new EmployeeChoice(e.getId(),e.getEmployeeCode(),e.getFullName(),e.getDateOfBirth())).toList();
        var person = context.employees().get(employeeId);
        String token = hash(json(Arrays.asList(staged.getId(),staged.getRowVersion(),staged.getPayload(),employeeId,
                person==null ? null : person.getRowVersion(),visit==null ? null : visit.getRowVersion(),cells,versions,errors)));
        return new PreviewRow(staged.getId(),source.sourceRow(),source.name(),source.code(),source.sid(),source.date(),staged.getState(),employeeId,candidates,
                List.copyOf(errors),List.copyOf(warnings),List.copyOf(cells),token,staged.getRowVersion(),
                source.corrections().stream().map(c -> new SourceCorrection(c.label(),c.original(),c.corrected(),c.reason(),c.at())).toList(),source.birth());
    }
    private static boolean same(HealthResult existing, HealthParsedData.Value incoming) {
        return Objects.equals(existing.getRawValue(),incoming.rawValue()) && (clean(incoming.reference()).isBlank() || clean(existing.getReferenceText()).equals(clean(incoming.reference())));
    }
}
