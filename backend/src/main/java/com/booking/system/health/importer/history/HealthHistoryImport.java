package com.booking.system.health.importer.history;

import com.booking.system.health.api.HrHealthHistoryDtos.*;
import com.booking.system.health.importer.baseline.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.importer.HrImportActor;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

@Service
@Slf4j
public class HealthHistoryImport {
    private final HealthHistoryStore store;
    private final HealthBaselinePreparation preparation;
    private final HealthBaselineAnalyzer analyzer;
    private final HealthHistoryConfirmer confirmer;
    private final HealthAccess access;
    private final HealthAuditService audit;
    private final HealthImportRepository imports;
    private final HealthImportRowRepository rows;
    private final HealthCampaignRepository campaigns;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("health-history-import").daemon(true).factory());
    @org.springframework.beans.factory.annotation.Value("${cfc.health.history.enabled:true}") private boolean enabled=true;
    private volatile String state="READY",message="";
    public HealthHistoryImport(HealthHistoryStore store,HealthBaselinePreparation preparation,HealthBaselineAnalyzer analyzer,HealthHistoryConfirmer confirmer,
            HealthAccess access,HealthAuditService audit,HealthImportRepository imports,HealthImportRowRepository rows,HealthCampaignRepository campaigns) {
        this.store=store;this.preparation=preparation;this.analyzer=analyzer;this.confirmer=confirmer;this.access=access;this.audit=audit;this.imports=imports;this.rows=rows;this.campaigns=campaigns;
    }
    public Status status(HrImportActor actor){access.requireManager(actor);return snapshot();}
    public Status unlock(byte[] key,HrImportActor actor) {
        access.requireManager(actor);
        try{store.install(key);}catch(IllegalArgumentException exception){throw com.booking.system.hr.api.HrApiException.badRequest("HEALTH_HISTORY_KEY",exception.getMessage());}
        finally{Arrays.fill(key,(byte)0);}
        audit.record("INSTALL_HISTORY_KEY","COMPANY_HISTORY",actor);
        return start(actor,true,"Kích hoạt dữ liệu lịch sử đã đóng gói.");
    }
    public Status start(HrImportActor actor,boolean force,String reason) {
        access.requireManager(actor);
        if(!enabled||!store.available()||!store.unlocked())return snapshot();
        if(busy.compareAndSet(false,true)) {
            state="RUNNING";message="Đang nhập dữ liệu lịch sử; bạn có thể tiếp tục dùng hệ thống.";
            worker.submit(()->{try{perform(actor,force,reason);}catch(RuntimeException error){state="ERROR";message="Chưa nhập xong dữ liệu lịch sử. Các lô đã tạo vẫn được giữ; có thể kiểm tra rồi thử lại.";log.warn("Health history import stopped: errorType={}",error.getClass().getSimpleName());}finally{busy.set(false);}});
        }
        return snapshot();
    }
    // Workbook parsing/decryption and identity analysis stay outside the catalog/staging transaction beans.
    public void execute(HrImportActor actor,boolean force,String reason) {
        access.requireManager(actor);perform(actor,force,reason);
    }
    private void perform(HrImportActor actor,boolean force,String reason) {
        var opened=store.open();var previous=store.receipt();
        if(!force&&previous!=null&&previous.hash().equals(opened.hash())&&imports.findAllById(previous.batchIds()).size()==previous.batchIds().size()){state="DONE";message="Dữ liệu lịch sử của bản phát hành này đã được xử lý; không nhập lại.";return;}
        var identities=preparation.identities();var dataset=opened.data().dataset();
        var report=analyzer.analyze(dataset,identities);
        var prepared=preparation.prepare(dataset,report,"CFC_HISTORY",Policy.TOTAL_FIRST,opened.data().sidYearsConfirmed(),actor);
        confirmer.confirm(prepared,identities,actor);
        var receipt=new HealthHistoryStore.Receipt(opened.hash(),prepared.batches().stream().map(b->b.id()).toList());
        store.receipt(receipt);audit.record("IMPORT_PACKAGED_HISTORY",opened.hash().substring(0,36),actor,reason);
        state="DONE";message="Đã xử lý gói lịch sử. Dòng khớp chắc chắn đã nhập; dòng thiếu mã, sai thông tin hoặc xung đột được giữ để rà soát. Chưa công bố cho cá nhân.";
    }
    private Status snapshot() {
        if(!enabled||!store.available())return new Status("UNAVAILABLE","Bản phát hành chưa kèm gói dữ liệu lịch sử.",List.of(),0,0);
        if(!store.unlocked())return new Status("LOCKED","Dữ liệu lịch sử đã đi kèm bản phát hành. Cấp file khóa một lần để tự nhập, không cần ánh xạ cột.",List.of(),0,0);
        List<Batch> batches=List.of();
        try{var receipt=store.receipt();if(receipt!=null)batches=details(receipt.batchIds());}
        catch(RuntimeException error){log.warn("Health history status unavailable: errorType={}",error.getClass().getSimpleName());return new Status("ERROR","Không đọc được trạng thái dữ liệu lịch sử; cần kiểm tra tệp trạng thái riêng trên máy chủ.",List.of(),0,0);}
        return new Status(state,message,batches,batches.stream().mapToLong(Batch::imported).sum(),batches.stream().mapToLong(Batch::pending).sum());
    }
    private List<Batch> details(List<String> ids) {
        if(ids.isEmpty())return List.of();
        var batches=imports.findAllById(ids);var byCampaign=new HashMap<String,com.booking.system.health.entity.HealthCampaign>();
        campaigns.findAllById(batches.stream().map(b->b.getCampaignId()).distinct().toList()).forEach(c->byCampaign.put(c.getId(),c));
        var counts=new HashMap<String,Map<String,Long>>();rows.countStates(ids).forEach(c->counts.computeIfAbsent(c.getBatchId(),id->new HashMap<>()).put(c.getState(),c.getTotal()));
        return batches.stream().filter(b->byCampaign.containsKey(b.getCampaignId())).map(b->{var campaign=byCampaign.get(b.getCampaignId());var count=counts.getOrDefault(b.getId(),Map.of());return new Batch(b.getId(),b.getCampaignId(),campaign.getReportYear(),campaign.getName(),b.getSourceSheet(),b.getTotalRows(),count.getOrDefault("IMPORTED",0L),count.getOrDefault("PENDING",0L),b.getStatus(),b.getRowVersion());}).sorted(Comparator.comparingInt(Batch::year).thenComparing(Batch::sheet)).toList();
    }
    @PreDestroy public void close(){worker.shutdownNow();}
}
