package com.booking.system.health.importer.history;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.importer.*;
import com.booking.system.health.importer.baseline.*;
import com.booking.system.health.repository.HealthVisitRepository;
import com.booking.system.hr.importer.HrImportActor;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Service
@RequiredArgsConstructor
public class HealthHistoryConfirmer {
    private final HealthImportService imports;
    private final HealthImportWriter writer;
    private final HealthVisitRepository visits;
    public void confirm(HealthBaselinePreparation.Prepared prepared,List<HealthBaselineData.Identity> identities,HrImportActor actor) {
        var byId=new HashMap<String,HealthBaselineData.Identity>();identities.forEach(i->byId.put(i.id(),i));
        for(var batch:prepared.batches())if(prepared.primaryBatchIds().contains(batch.id())) {
            for(int page=0;page<125;page++) {
                var values=imports.preview(batch.id(),page,20,actor);
                var sids=values.content().stream().map(PreviewRow::sid).filter(s->!s.isBlank()).toList();
                var external=sids.isEmpty()?Set.<String>of():new HashSet<>(visits.findBySourceSidIn(sids).stream().filter(v->!v.getSourceNamespace().equals("CFC_HISTORY")).map(v->v.getSourceSid()).toList());
                var decisions=values.content().stream().filter(r->eligible(r,byId.get(r.employeeId()))&&!external.contains(r.sid()))
                    .map(r->new RowDecision(r.id(),r.previewToken(),Map.<String,Choice>of(),null)).toList();
                if(!decisions.isEmpty())writer.confirm(batch.id(),new ConfirmRequest(decisions),actor);
                if(values.last())break;
            }
        }
    }
    private static boolean eligible(PreviewRow row,HealthBaselineData.Identity identity) {
        if(identity==null||!row.state().equals("PENDING")||!row.errors().isEmpty()||row.code().isBlank()||!row.code().equalsIgnoreCase(identity.code())||row.sid().isBlank())return false;
        if(row.cells().stream().anyMatch(c->c.action().equals("CONFLICT")))return false;
        if(!row.sourceBirth().isBlank()&&(identity.birth()==null||identity.birth().isBlank()))return false;
        var source=new HealthParsedData.Row(row.sourceRow(),row.code(),row.name(),row.sourceBirth(),row.sid(),row.examinedOn(),List.of(),List.of(),List.of());
        return HealthBaselineAnalyzer.samePerson(source,identity);
    }
}
