package com.booking.system.health.importer.baseline;

import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Service
@RequiredArgsConstructor
public class HealthBaselineCatalog {
    private final HealthCampaignRepository campaigns;
    private final HealthIndicatorRepository indicators;
    private final HealthAccess access;
    private final HealthAuditService audit;
    public record Catalog(Map<Integer,String> campaigns,Map<String,String> indicators) { }

    @Transactional
    public Catalog prepare(Map<String,String> labels,HrImportActor actor) {
        access.requireManager(actor);
        if(labels.size()>1000) throw invalid("Baseline vượt 1.000 chỉ tiêu nguồn; cần chia đợt rà soát.");
        var campaignCodes=List.of("HISTORY_2023","HISTORY_2024","HISTORY_2025");
        var years=campaigns.findByCodeIn(campaignCodes).stream().collect(Collectors.toMap(HealthCampaign::getReportYear,Function.identity()));
        var additions=new ArrayList<HealthCampaign>();
        for(int year:List.of(2023,2024,2025)) if(!years.containsKey(year)) {
            var campaign=new HealthCampaign();campaign.setCode("HISTORY_"+year);campaign.setReportYear(year);campaign.setName("Lịch sử khám sức khỏe "+year);
            campaign.setProvider("Nguồn tổng hợp công ty — cơ sở khám cần xác minh");campaign.setWorkforceGroup(HealthTypes.WorkforceGroup.BOTH);campaign.setStatus(HealthTypes.CampaignStatus.OPEN);
            HealthAccess.audit(campaign,actor,true);years.put(year,campaign);additions.add(campaign);
        }
        if(years.values().stream().anyMatch(c -> c.getStatus()!=HealthTypes.CampaignStatus.OPEN)) throw invalid("Đợt baseline đã khóa; không tự mở lại.");
        campaigns.saveAll(additions);campaigns.flush();
        var targets=labels.entrySet().stream().collect(Collectors.toMap(e -> canonicalCode(e.getKey(),e.getValue()),Map.Entry::getValue,(a,b)->a,LinkedHashMap::new));
        var catalog=targets.isEmpty() ? new HashMap<String,HealthIndicator>() : indicators.findByCodeIn(targets.keySet()).stream().collect(Collectors.toMap(HealthIndicator::getCode,Function.identity()));
        var newIndicators=new ArrayList<HealthIndicator>();
        for(var entry:targets.entrySet()) if(!catalog.containsKey(entry.getKey())) {
            var indicator=new HealthIndicator();indicator.setCode(entry.getKey());indicator.setName(entry.getValue().substring(0,Math.min(entry.getValue().length(),255)));
            boolean classification=HealthClassification.isClassification(entry.getValue());indicator.setCategory(classification ? "Phân loại sức khỏe" : "Lịch sử — chờ rà soát danh mục");
            indicator.setValueType(classification ? HealthTypes.IndicatorType.CODED : HealthTypes.IndicatorType.TEXT);indicator.setStatus(HealthTypes.IndicatorStatus.ACTIVE);
            HealthAccess.audit(indicator,actor,true);catalog.put(entry.getKey(),indicator);newIndicators.add(indicator);
        }
        if(catalog.values().stream().anyMatch(i -> i.getStatus()!=HealthTypes.IndicatorStatus.ACTIVE)) throw invalid("Chỉ tiêu baseline đã ngừng dùng; cần quản trị xác nhận.");
        indicators.saveAll(newIndicators);indicators.flush();
        var mapping=labels.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e -> catalog.get(canonicalCode(e.getKey(),e.getValue())).getId()));
        audit.record("PREPARE_HISTORICAL_CATALOG","BASELINE_2023_2025",actor);
        return new Catalog(years.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e -> e.getValue().getId())),mapping);
    }
    private static String canonicalCode(String key,String label) {return HealthClassification.isClassification(label) ? "HEALTH_CLASSIFICATION" : key;}
}
