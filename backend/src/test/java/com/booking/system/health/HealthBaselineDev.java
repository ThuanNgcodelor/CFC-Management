package com.booking.system.health;

import com.booking.system.health.importer.*;
import com.booking.system.health.importer.baseline.*;
import com.booking.system.hr.importer.HrImportActor;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

/** Local operator tool, compiled only to test-classes; never included in the production JAR. */
public final class HealthBaselineDev {
    private static final HrImportActor ACTOR=new HrImportActor("USER:health-baseline-dev","Baseline dev operator","ADMIN");
    private HealthBaselineDev() { }
    public static void main(String[] args) throws Exception {
        Map<String,String> options=options(args);Path input=Path.of(required(options,"input")).toAbsolutePath().normalize();
        Path output=Path.of(required(options,"output")).toAbsolutePath().normalize();
        if(!output.startsWith(Path.of("/tmp"))||!output.toString().matches("[a-zA-Z0-9_./-]+")) throw new IllegalArgumentException("Dữ liệu review phải nằm ở thư mục /tmp riêng, không trong checkout.");
        Files.createDirectories(output);Files.setPosixFilePermissions(output,PosixFilePermissions.fromString("rwx------"));
        var inputs=inputs(input);var reader=new HealthBaselineReader(new HealthWorkbookParser());var dataset=reader.read(inputs);
        List<Identity> identities=options.containsKey("identities") ? List.of(HealthImportSupport.read(Files.readString(Path.of(options.get("identities"))),Identity[].class)) : List.of();
        validateIdentities(identities);
        var report=new HealthBaselineAnalyzer().analyze(dataset,identities);
        HealthBaselineReportWriter.write(output,dataset,report,inputs);
        if(!options.containsKey("stage")) {printSummary(report,0);return;}
        Policy policy=Policy.valueOf(required(options,"policy"));
        if(!options.containsKey("identities")) throw new IllegalArgumentException("Cần snapshot định danh nhân sự được phép dùng trước khi stage baseline vào DB dev.");
        var app=new SpringApplication(HealthPhase2Test.Config.class);
        try(var context=app.run(serverArguments(output))) {
            var logging=org.springframework.boot.logging.LoggingSystem.get(HealthBaselineDev.class.getClassLoader());
            for(String logger:List.of("ROOT","org.springframework","org.hibernate","com.booking")) logging.setLogLevel(logger,org.springframework.boot.logging.LogLevel.INFO);
            var jdbc=context.getBean(JdbcTemplate.class);
            try(var connection=jdbc.getDataSource().getConnection()) {requireLocalDatabase(connection.getMetaData().getURL());}
            backup(jdbc,output);bootstrapIdentities(jdbc,identities,output);
            var preparation=context.getBean(HealthBaselinePreparation.class);
            var prepared=preparation.prepare(dataset,report,options.getOrDefault("namespace","CFC_HISTORY"),policy,options.containsKey("confirm-sid-years"),ACTOR);
            if(options.containsKey("confirm-sid-years")) {
                var confirmation=context.getBean(HealthBaselineYearConfirmation.class);int yearsConfirmed=0;
                for(var batch:prepared.batches()) yearsConfirmed+=confirmation.confirm(batch.id(),"Người quản trị xác nhận năm 2023/2024 theo SID; chưa xác nhận ngày khám.",ACTOR);
                System.out.println("HEALTH_BASELINE_YEARS_CONFIRMED rows="+yearsConfirmed);
            }
            HealthBaselineReportWriter.writePrivate(output.resolve("baseline-receipt-private.json"),HealthImportSupport.json(prepared).getBytes(StandardCharsets.UTF_8));
            int confirmed=options.containsKey("confirm-code-matches") ? confirmReady(context,prepared,policy) : 0;
            printSummary(report,confirmed);
            try(var files=Files.list(output)) {for(var file:files.filter(Files::isRegularFile).toList()) Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));}
        }
    }
    public static void requireLocalDatabase(String url) {
        if(url==null || !(url.startsWith("jdbc:h2:file:/tmp/")||url.startsWith("jdbc:h2:mem:"))) throw new IllegalArgumentException("Công cụ baseline dev chỉ được ghi H2 riêng; từ chối MySQL/TCP/server.");
    }
    private static List<WorkbookInput> inputs(Path root) throws Exception {
        return List.of(new WorkbookInput(Kind.HISTORY_2023_2024,"So sánh kết quả sức khỏe gián tiếp 2023 - 2024.xlsx",Files.readAllBytes(root.resolve("So sánh kết quả sức khỏe gián tiếp 2023 - 2024.xlsx"))),
            new WorkbookInput(Kind.SUPPLEMENT_2024,"bo sung thong tin SK 2024.xlsx",Files.readAllBytes(root.resolve("bo sung thong tin SK 2024.xlsx"))),
            new WorkbookInput(Kind.RESULTS_2025,"KHAM SUC KHOE 2025.xlsx",Files.readAllBytes(root.resolve("KHAM SUC KHOE 2025.xlsx"))));
    }
    private static void validateIdentities(List<Identity> values) {
        if(values.size()>20000) throw new IllegalArgumentException("Snapshot tối đa 20.000 nhân sự.");
        var ids=new HashSet<String>();var codes=new HashSet<String>();
        for(var item:values) if(item.id()==null||item.id().isBlank()||item.id().length()>36||item.name()==null||item.name().isBlank()||item.name().length()>255
                ||item.code()==null||item.code().isBlank()||item.code().length()>32||!ids.add(item.id())||!codes.add(item.code().strip().toUpperCase(Locale.ROOT)))
            throw new IllegalArgumentException("Snapshot định danh thiếu/trùng ID hoặc mã; không tự sửa nhân sự.");
    }
    private static void bootstrapIdentities(JdbcTemplate jdbc,List<Identity> identities,Path output) throws Exception {
        Path marker=output.resolve("identity-installed.sha256");String fingerprint=HealthImportSupport.hash(HealthImportSupport.json(identities));
        if(Files.exists(marker)) {
            if(!Files.readString(marker).equals(fingerprint)) throw new IllegalArgumentException("Snapshot đã đổi; dùng thư mục DB dev mới hoặc review liên kết trước khi đồng bộ lại.");
            return;
        }
        Integer healthRows=jdbc.queryForObject("select (select count(*) from hr_health_imports)+(select count(*) from hr_health_visits)+(select count(*) from hr_health_subjects)",Integer.class);
        if(healthRows!=null && healthRows>0) throw new IllegalArgumentException("DB dev đã có sức khỏe nhưng chưa có dấu snapshot; từ chối thay dữ liệu nhân sự.");
        // Flyway's historical HR seeds are not today's roster. Only this disposable H2 clone is reset.
        var tables=jdbc.queryForList("select table_name from information_schema.tables where table_schema='public'",String.class).stream()
            .filter(name -> name.startsWith("hr_") && name.matches("[a-z0-9_]+" )).toList();
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {for(String table:tables) jdbc.execute("DELETE FROM "+table);}
        finally {jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");}
        var additions=new ArrayList<Object[]>();
        for(var identity:identities) {
            java.sql.Date birth=identity.birth()!=null && identity.birth().matches("\\d{4}-\\d{2}-\\d{2}") ? java.sql.Date.valueOf(identity.birth()) : null;
            additions.add(new Object[]{identity.id(),identity.code(),identity.name(),birth,ACTOR.subject(),ACTOR.subject()});
        }
        jdbc.batchUpdate("insert into hr_employees (id,employee_code,full_name,date_of_birth,created_by_actor,updated_by_actor) values (?,?,?,?,?,?)",additions);
        HealthBaselineReportWriter.writePrivate(marker,fingerprint.getBytes(StandardCharsets.UTF_8));
    }
    private static void backup(JdbcTemplate jdbc,Path output) throws Exception {
        Path target=output.resolve("before-health-"+java.time.Instant.now().toEpochMilli()+".sql");
        Files.createFile(target,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        jdbc.execute("SCRIPT TO '"+target+"'");
        if(Files.size(target)==0) throw new IllegalStateException("Backup DB dev rỗng; dừng nhập.");
    }
    private static int confirmReady(org.springframework.context.ConfigurableApplicationContext context,HealthBaselinePreparation.Prepared prepared,Policy policy) {
        if(policy!=Policy.TOTAL_FIRST) throw new IllegalArgumentException("Chưa chọn nguồn ưu tiên: không tự xác nhận từ STAGE_ALL.");
        var imports=context.getBean(HealthImportService.class);var writer=context.getBean(HealthImportWriter.class);int count=0;
        for(var batch:prepared.batches()) {
            if(!prepared.primaryBatchIds().contains(batch.id())) continue;
            for(int page=0;page<125;page++) {
                var result=imports.preview(batch.id(),page,20,ACTOR);
                var decisions=result.content().stream().filter(row -> row.state().equals("PENDING") && row.errors().isEmpty() && row.cells().stream().noneMatch(cell -> cell.action().equals("CONFLICT")))
                    .map(row -> new com.booking.system.health.api.HrHealthImportDtos.RowDecision(row.id(),row.previewToken(),Map.<String,com.booking.system.health.api.HrHealthImportDtos.Choice>of(),null)).toList();
                if(!decisions.isEmpty()) count+=writer.confirm(batch.id(),new com.booking.system.health.api.HrHealthImportDtos.ConfirmRequest(decisions),ACTOR).importedRows();
                if(page+1>=result.totalPages()) break;
            }
        }
        return count;
    }
    private static String[] serverArguments(Path output) {
        return new String[]{"--spring.config.location=optional:file:/tmp/health-baseline-empty.properties","--spring.config.import=","--spring.profiles.active=health-baseline-dev",
            "--server.address=127.0.0.1","--server.port=0","--spring.datasource.url=jdbc:h2:file:"+output.resolve("health-dev-db")+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=","--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.show-sql=false","--spring.jpa.open-in-view=false","--spring.flyway.enabled=true","--cfc.health.enabled=true","--spring.h2.console.enabled=false","--logging.level.root=INFO","--logging.level.org.springframework.web=INFO","--logging.level.org.hibernate.SQL=OFF"};
    }
    private static Map<String,String> options(String[] args) {
        var options=new HashMap<String,String>();var flags=Set.of("stage","confirm-sid-years","confirm-code-matches");
        var names=Set.of("input","output","identities","policy","namespace");
        for(int i=0;i<args.length;i++) {
            if(!args[i].startsWith("--")) throw new IllegalArgumentException("Tham số phải bắt đầu bằng --.");String key=args[i].substring(2);
            if(flags.contains(key)) options.put(key,"true");else if(names.contains(key)&&i+1<args.length) options.put(key,args[++i]);else throw new IllegalArgumentException("Tham số baseline không hợp lệ.");
        }
        return options;
    }
    private static String required(Map<String,String> options,String key) {if(!options.containsKey(key)) throw new IllegalArgumentException("Thiếu --"+key);return options.get(key);}
    private static void printSummary(Report report,int confirmed) {System.out.println("HEALTH_BASELINE_DEV_OK sourceRows="+report.sourceRows()+" uniqueSids="+report.distinctSids()+" tasks="+report.taskRows()+" codeMatched="+report.matchedCodeRows()+" confirmed="+confirmed);}
}
