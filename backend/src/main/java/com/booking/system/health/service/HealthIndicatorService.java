package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthDtos.*;
import com.booking.system.health.entity.HealthIndicator;
import com.booking.system.health.entity.HealthIndicatorAlias;
import com.booking.system.health.repository.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthIndicatorService {
    private final HealthIndicatorRepository indicators;
    private final HealthIndicatorAliasRepository aliases;
    private final HealthAuditService audit;
    private final HealthAccess access;

    @Transactional(readOnly = true)
    public HrPageResponse<IndicatorResponse> list(String keyword, int page, int size) {
        access.requireEnabled();
        var result = indicators.findAll(HealthQueries.keyword(keyword), HealthQueries.page(page, size, false));
        var names = result.isEmpty() ? Map.<String, List<String>>of() : aliases.findByIndicatorIdIn(result.map(HealthIndicator::getId).getContent())
                .stream().collect(Collectors.groupingBy(HealthIndicatorAlias::getIndicatorId,
                        Collectors.mapping(HealthIndicatorAlias::getName, Collectors.toList())));
        return HrPageResponse.from(result, value -> response(value, names.getOrDefault(value.getId(), List.of()).stream().sorted().toList()));
    }
    @Transactional
    public IndicatorResponse create(IndicatorRequest request, HrImportActor actor) {
        access.requireManager(actor);
        if (indicators.existsByCode(HealthQueries.code(request.code()))) throw HrApiException.conflict("HEALTH_CODE_EXISTS", "Mã chỉ tiêu đã tồn tại.");
        var names = validateAliases(request.aliases(), "");
        HealthIndicator entity = new HealthIndicator();
        entity.setCode(HealthQueries.code(request.code()));
        apply(entity, request); HealthAccess.audit(entity, actor, true);
        indicators.saveAndFlush(entity);
        replaceAliases(entity.getId(), names, actor);
        audit.record("CREATE_INDICATOR", entity.getId(), actor);
        return response(entity, names);
    }
    @Transactional
    public IndicatorResponse update(String id, IndicatorRequest request, HrImportActor actor) {
        access.requireManager(actor);
        HealthIndicator entity = indicators.findById(id).orElseThrow(() -> HrApiException.notFound("HEALTH_INDICATOR_NOT_FOUND", "Không tìm thấy chỉ tiêu."));
        HealthAccess.checkVersion(entity, request.rowVersion());
        if (!entity.getCode().equals(HealthQueries.code(request.code()))) throw HrApiException.badRequest("HEALTH_CODE_IMMUTABLE", "Mã chỉ tiêu không thay đổi sau khi tạo.");
        if (entity.getValueType() != request.valueType()) throw HrApiException.badRequest("HEALTH_TYPE_IMMUTABLE", "Kiểu kết quả không thay đổi sau khi tạo; hãy tạo chỉ tiêu khác khi cần.");
        var names = validateAliases(request.aliases(), id);
        apply(entity, request); HealthAccess.audit(entity, actor, false);
        // Alias-only edits must also advance the aggregate version.
        indicators.saveAndFlush(entity);
        replaceAliases(id, names, actor);
        audit.record("UPDATE_INDICATOR", id, actor);
        return response(entity, names);
    }
    private List<String> validateAliases(List<String> values, String id) {
        var names = values.stream().map(String::trim).toList();
        var normalized = names.stream().map(HealthIndicatorService::normalize).toList();
        if (new HashSet<>(normalized).size() != normalized.size()) throw HrApiException.badRequest("HEALTH_ALIAS_DUPLICATE", "Tên cột tương đương bị lặp.");
        if (!normalized.isEmpty() && aliases.existsByNormalizedNameInAndIndicatorIdNot(normalized, id)) {
            throw HrApiException.conflict("HEALTH_ALIAS_CONFLICT", "Tên cột tương đương đã thuộc chỉ tiêu khác.");
        }
        return names;
    }
    private void replaceAliases(String id, List<String> names, HrImportActor actor) {
        aliases.deleteByIndicatorId(id); aliases.flush();
        var records = names.stream().map(name -> {
            var alias = new HealthIndicatorAlias(); alias.setIndicatorId(id); alias.setName(name);
            alias.setNormalizedName(normalize(name)); HealthAccess.audit(alias, actor, true); return alias;
        }).toList();
        aliases.saveAll(records); aliases.flush();
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
    private static void apply(HealthIndicator entity, IndicatorRequest request) {
        entity.setName(request.name().trim()); entity.setCategory(request.category().trim());
        entity.setValueType(request.valueType()); entity.setStatus(request.status());
    }
    private static IndicatorResponse response(HealthIndicator value, List<String> names) {
        return new IndicatorResponse(value.getId(), value.getCode(), value.getName(), value.getCategory(), value.getValueType(),
                value.getStatus(), names, value.getRowVersion());
    }
}
