package com.booking.system.health.importer;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.HealthAccess;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Service
@RequiredArgsConstructor
public class HealthImportService {
    private final HealthWorkbookParser parser;
    private final HealthImportStaging staging;
    private final HealthImportContext context;
    private final HealthImportPreview preview;
    private final HealthImportRowRepository rows;
    private final HealthCampaignRepository campaigns;
    private final HealthEmployeeRepository employees;
    private final HealthMappingRepository profiles;
    private final HealthIndicatorRepository indicators;
    private final HealthIndicatorAliasRepository aliases;
    private final HealthAccess access;

    // Workbook I/O is completed before the separate persistence bean opens its transaction.
    public Batch stage(byte[] file, StageRequest request, HrImportActor actor) {
        access.requireManager(actor);
        return staging.stage(parser.parse(file,request),actor);
    }
    public Inspection inspect(byte[] file, HrImportActor actor) {
        return inspect(file,null,null,1,actor);
    }
    public Inspection inspect(byte[] file, String selectedSheet, Integer headerRow, int headerRows, HrImportActor actor) {
        access.requireManager(actor); var inspected = parser.inspect(file,selectedSheet,headerRow,headerRows);
        var names = inspected.sheets().stream().flatMap(s -> s.columns().stream()).map(c -> aliasName(c.name())).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        var matches = new HashMap<String,Set<String>>();
        if (!names.isEmpty()) {
            for (var alias : aliases.findByNormalizedNameIn(names)) matches.computeIfAbsent(alias.getNormalizedName(),key -> new HashSet<>()).add(alias.getIndicatorId());
            for (var indicator : indicators.matchNames(names,PageRequest.of(0,MAX_COLUMNS*MAX_SHEETS_FOR_LOOKUP))) {
                matches.computeIfAbsent(aliasName(indicator.getName()),key -> new HashSet<>()).add(indicator.getId());
                matches.computeIfAbsent(aliasName(indicator.getCode()),key -> new HashSet<>()).add(indicator.getId());
            }
        }
        var sheets = inspected.sheets().stream().map(sheet -> new SheetInfo(sheet.name(),sheet.hidden(),sheet.headerRow(),sheet.dataRows(),sheet.resultSheet(),sheet.fingerprint(),
                sheet.columns().stream().map(column -> {
                    Set<String> ids=matches.getOrDefault(aliasName(column.name()),Set.of());
                    return ids.size()==1 && column.suggested()==Field.IGNORE ? new Column(column.index(),column.letter(),column.name(),Field.RESULT,ids.iterator().next()) : column;
                }).toList())).toList();
        return new Inspection(inspected.fileHash(),sheets);
    }
    private static final int MAX_SHEETS_FOR_LOOKUP = 30;
    private static String aliasName(String value) {
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
    }
    @Transactional(readOnly=true)
    public HrPageResponse<PreviewRow> preview(String id, int page, int size, HrImportActor actor) {
        return preview(id,page,size,"ALL",actor);
    }
    @Transactional(readOnly=true)
    public HrPageResponse<PreviewRow> preview(String id,int page,int size,String filter,HrImportActor actor) {
        access.requireManager(actor); HealthImportStaging.checkPage(page,size);
        if(!Set.of("ALL","PENDING","IMPORTED","CANCELLED").contains(filter))throw invalid("Trạng thái dòng nhập không hợp lệ.");
        var batch=staging.requireBatch(id); var campaign=campaigns.findById(batch.getCampaignId()).orElseThrow();
        var result="ALL".equals(filter)?rows.findByBatchIdOrderBySourceRow(id,PageRequest.of(page,size))
            :rows.findByBatchIdAndStateOrderBySourceRow(id,filter,PageRequest.of(page,size));
        var state=context.load(batch,result.getContent());
        return HrPageResponse.from(result,row -> preview.row(batch,row,state,campaign.getReportYear()));
    }
    @Transactional(readOnly=true)
    public List<EmployeeChoice> employees(String keyword, HrImportActor actor) {
        access.requireManager(actor);
        if (keyword==null || keyword.trim().length()<2 || keyword.length()>100) throw invalid("Nhập từ 2 đến 100 ký tự để tìm nhân sự.");
        String pattern="%"+keyword.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return employees.search(pattern,PageRequest.of(0,20)).stream().map(e -> new EmployeeChoice(e.getId(),e.getEmployeeCode(),e.getFullName(),e.getDateOfBirth())).toList();
    }
    @Transactional(readOnly=true)
    public HrPageResponse<Profile> profiles(String fingerprint, int page, int size, HrImportActor actor) {
        access.requireManager(actor); HealthImportStaging.checkPage(page,size);
        if (fingerprint==null || !fingerprint.matches("[a-f0-9]{64}")) throw invalid("Chưa chọn cấu trúc sheet hợp lệ.");
        return HrPageResponse.from(profiles.findByFingerprintOrderByName(fingerprint,PageRequest.of(page,size)),p -> new Profile(p.getId(),p.getName(),p.getFingerprint(),p.getMappingJson(),p.getRowVersion()));
    }
}
