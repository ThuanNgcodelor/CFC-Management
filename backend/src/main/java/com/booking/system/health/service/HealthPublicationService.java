package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.ConcurrencyFailureException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthPublicationService {
    private final HealthAccess access;
    private final HealthPublicationWriter writer;
    public BatchResult apply(BatchRequest request,boolean review,HrImportActor actor) {
        return applyAction(request,review?"REVIEW":"PUBLISH",actor);
    }
    public BatchResult revoke(BatchRequest request,HrImportActor actor){return applyAction(request,"REVOKE",actor);}
    private BatchResult applyAction(BatchRequest request,String action,HrImportActor actor) {
        access.requireManager(actor);
        if(new HashSet<>(request.people().stream().map(PersonDecision::employeeId).toList()).size()!=request.people().size())throw HrApiException.badRequest("HEALTH_PUBLICATION_DUPLICATE","Không chọn lặp nhân viên.");
        var outcomes=new ArrayList<Outcome>();
        for(var person:request.people()) {
            try{outcomes.add(writer.apply(request.campaignId(),person,request.reason(),request.acknowledgeIncomplete(),action,actor));}
            catch(HrApiException exception){outcomes.add(new Outcome(person.employeeId(),"BLOCKED",0,exception.getMessage()));}
            catch(ConcurrencyFailureException exception){log.warn("Concurrent health publication operation: type={}",exception.getClass().getSimpleName());outcomes.add(new Outcome(person.employeeId(),"CONFLICT",0,"Có thao tác đồng thời; hãy xem trước lại rồi thử tiếp."));}
        }
        return new BatchResult((int)outcomes.stream().filter(v->v.status().equals("SUCCESS")).count(),(int)outcomes.stream().filter(v->v.status().equals("SKIPPED")).count(),(int)outcomes.stream().filter(v->Set.of("BLOCKED","CONFLICT").contains(v.status())).count(),List.copyOf(outcomes));
    }
}
