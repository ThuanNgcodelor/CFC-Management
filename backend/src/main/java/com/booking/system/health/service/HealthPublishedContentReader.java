package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.repository.*;
import com.booking.system.hr.api.HrApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import com.booking.system.health.entity.HealthPublication;
import static com.booking.system.health.importer.HealthImportSupport.*;

/** Reads immutable releases only. Callers must establish the owner through their own access boundary. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthPublishedContentReader {
    private final HealthPublicationRepository publications;

    public HealthPublishedReader.Content current(String employee, String id) {
        var publication = publications.ownedCurrent(employee, id).orElseThrow(HealthPublishedContentReader::missing);
        return validate(employee, publication);
    }
    public HealthPublishedReader.Content validate(String employee, HealthPublication publication) {
        if (!"PUBLISHED".equals(publication.getStatus())) throw missing();
        if (publication.getContentHash() == null || publication.getSnapshot().getBytes(StandardCharsets.UTF_8).length > HealthPublicationDrafts.MAX_SNAPSHOT_BYTES) throw missing();
        var snapshot = read(publication.getSnapshot(), PublishedSnapshot.class);
        if (snapshot.format() != 1 || !employee.equals(snapshot.person().employeeId()) || !publication.getVisitId().equals(snapshot.visit().id())) throw missing();
        String contentHash = hash(json(new PublishedSnapshot(1, snapshot.person(), snapshot.visit(), snapshot.results(), snapshot.incomplete(), null)));
        if (!contentHash.equals(publication.getContentHash())) throw missing();
        var info = new PublicationInfo(publication.getId(), publication.getVisitId(), publication.getRevision(), publication.getStatus(),
                publication.getRowVersion(), publication.getContentHash(), publication.getPublishReason(), publication.getRevokeReason(), publication.getCreatedAt(), publication.getCreatedByActor());
        return new HealthPublishedReader.Content(info, snapshot);
    }
    private static HrApiException missing() {
        return HrApiException.notFound("HEALTH_PUBLICATION_UNAVAILABLE", "Phiên bản không còn được phép hiển thị hoặc không thuộc người được chọn.");
    }
}
