package com.booking.system.health.importer;

import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.hr.entity.HrEmployee;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Component
@RequiredArgsConstructor
public class HealthImportContext {
    private final HealthEmployeeRepository employees;
    private final HealthSubjectRepository subjects;
    private final HealthVisitRepository visits;
    private final HealthResultRepository results;
    private final HealthIndicatorRepository indicators;

    public record State(Map<String,HrEmployee> employees, Map<String,HealthSubject> subjects,
                        Map<String,HealthVisit> visits, Map<String,HealthResult> results, Map<String,HealthIndicator> indicators) { }
    public State load(HealthImport batch, List<HealthImportRow> staged) {
        var data = staged.stream().filter(r -> r.getPayload()!=null).map(r -> read(r.getPayload(),HealthParsedData.Row.class)).toList();
        var codes = data.stream().map(HealthParsedData.Row::code).collect(Collectors.toSet());
        var names = data.stream().map(r -> r.name().toLowerCase(Locale.ROOT).trim()).collect(Collectors.toSet());
        var people = new HashMap<String,HrEmployee>();
        if (!data.isEmpty()) employees.candidates(codes,names,PageRequest.of(0,201)).forEach(e -> people.put(e.getId(),e));
        var manual = staged.stream().map(HealthImportRow::getEmployeeId).filter(Objects::nonNull).distinct().toList();
        if (!manual.isEmpty()) employees.findAllById(manual).forEach(e -> people.put(e.getId(),e));
        var subjectMap = people.isEmpty() ? Map.<String,HealthSubject>of() : subjects.findByEmployeeIdIn(people.keySet()).stream().collect(Collectors.toMap(HealthSubject::getEmployeeId,Function.identity()));
        var keys = new HashSet<String>();
        for (var row : staged) if (row.getPayload()!=null) {
            var source = read(row.getPayload(),HealthParsedData.Row.class);
            String person = match(row,source,people);
            String key = visitKey(source,person,batch.getCampaignId());
            if (key!=null) keys.add(key);
        }
        var visitMap = keys.isEmpty() ? Map.<String,HealthVisit>of() : visits.findBySourceNamespaceAndSourceSidIn(batch.getSourceNamespace(),keys).stream().collect(Collectors.toMap(HealthVisit::getSourceSid,Function.identity()));
        var visitIds = visitMap.values().stream().map(HealthVisit::getId).toList();
        var resultMap = visitIds.isEmpty() ? Map.<String,HealthResult>of() : results.findByVisitIdIn(visitIds).stream().collect(Collectors.toMap(v -> v.getVisitId()+":"+v.getValueKey(), Function.identity()));
        var ids = data.stream().flatMap(r -> r.values().stream()).map(HealthParsedData.Value::indicatorId).collect(Collectors.toSet());
        var indicatorMap = new HashMap<String,HealthIndicator>();
        if (!ids.isEmpty()) indicators.findAllById(ids).forEach(v -> indicatorMap.put(v.getId(),v));
        return new State(people,subjectMap,visitMap,resultMap,indicatorMap);
    }
    public static String match(HealthImportRow row, HealthParsedData.Row source, Map<String,HrEmployee> people) {
        if (row.getEmployeeId()!=null) return people.containsKey(row.getEmployeeId()) ? row.getEmployeeId() : null;
        var matches = people.values().stream().filter(e -> e.getEmployeeCode().equalsIgnoreCase(source.code())
                && normalized(e.getFullName()).equals(normalized(source.name())) && birthMatches(source.birth(),e)).toList();
        return matches.size()==1 ? matches.getFirst().getId() : null;
    }
    private static boolean birthMatches(String birth, HrEmployee employee) {
        if (birth.isBlank() || employee.getDateOfBirth()==null) return true;
        String year = Integer.toString(employee.getDateOfBirth().getYear());
        if (birth.matches("\\d{4}")) return birth.equals(year);
        for (String format : List.of("uuuu-MM-dd","d/M/uuuu","d-M-uuuu")) {
            try { return java.time.LocalDate.parse(birth, java.time.format.DateTimeFormatter.ofPattern(format)
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT)).equals(employee.getDateOfBirth()); }
            catch (java.time.DateTimeException ex) { /* A different source date format may apply. */ }
        }
        return false;
    }
    public static String visitKey(HealthParsedData.Row source, String employeeId, String campaignId) {
        if (!source.sid().isBlank()) return source.sid();
        if (employeeId==null || source.date()==null) return null;
        return "NO-SID:"+hash(employeeId+"|"+campaignId+"|"+source.date()).substring(0,60);
    }
}
