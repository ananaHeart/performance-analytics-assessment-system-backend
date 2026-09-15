package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.AssessmentApplication;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.*;
import com.capstone.assessment.v3.scoring.service.V3ScoringService;
import com.capstone.assessment.v3.system.service.V3MobileReleaseReadinessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.nio.file.*;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opted-in isolated clone only. Never resolves the normal app database or port. */
@EnabledIfSystemProperty(named="v3.dynamic.database",matches="v3_dynamic_staging_[0-9]+")
class V3DynamicStagingTest {
    private JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private static String uuid(){return UUID.randomUUID().toString();}
    @Test void generateBoundarySheetsAndFinalizeEveryPage() throws Exception {
        String db=System.getProperty("v3.dynamic.database");
        assertTrue(db.matches("v3_dynamic_staging_[0-9]+"));
        Path evidence=Files.createDirectories(Path.of(System.getProperty("v3.dynamic.evidence")).toAbsolutePath());
        var args=new ArrayList<String>(List.of("--spring.profiles.active=v3,v3-mobile-release,v3-dynamic-staging","--server.port=0","--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1:33319/"+db+"?useSSL=false&serverTimezone=UTC",
                "--spring.datasource.username=root","--spring.datasource.password=dynamic_staging_local",
                "--app.v3.baseline.expected-database="+db,"--app.v3.baseline.expected-omr-template-count=2",
                "--app.v3.baseline.expected-approved-omr-template-count=2","--app.v3.mobile.release.http-enabled=false",
                "--app.v3.mobile.release.public-base-url=http://127.0.0.1:8080","--app.v3.mobile.release.adb-reverse-enabled=true",
                "--app.v3.scan-evidence.storage-directory="+Files.createDirectories(evidence.resolve("scan-evidence")),
                "--app.v3.auth.cleanup-enabled=false","--spring.main.banner-mode=off","--logging.level.org.springframework=INFO"));
        for(String feature:List.of("scan-recovery","finalization","readback","evaluation-reference","reopen","correction","supersede"))args.add("--app.v3.mobile."+feature+"-enabled=true");
        try(var app=new SpringApplication(AssessmentApplication.class).run(args.toArray(String[]::new))) {
            jdbc=app.getBean(JdbcTemplate.class);assertEquals(db,jdbc.queryForObject("SELECT DATABASE()",String.class));
            var readiness=app.getBean(V3MobileReleaseReadinessService.class);
            var preflight=readiness.checkReadiness();
            assertTrue(preflight.checks().stream().filter(c->!c.check().equals("write_api_switch")).allMatch(c->c.passed()),preflight.toString());
            app.getBean(V3MobileReleaseProperties.class).getRelease().setHttpEnabled(true);
            assertTrue(readiness.requireReady().backendReady());
            var generator=app.getBean(V3AnswerSheetService.class);
            var mobile=app.getBean(V3MobileService.class);
            long teacherId=jdbc.queryForObject("SELECT c.user_id FROM test_assignments t JOIN class_assignments c ON c.class_assignment_id=t.class_assignment_id WHERE t.test_assignment_id=1006",Long.class);
            String school=jdbc.queryForObject("SELECT school_id FROM users WHERE user_id=?",String.class,teacherId);
            var user=new V3AuthenticatedUser(teacherId,school,"","teacher","active","isolated-validation");
            var audit=new V3RequestMetadata("127.0.0.1","dynamic-staging-validation","synthetic-pdf-capture");
            var outputs=mapper.createArrayNode();
            for(int items:List.of(4,5,17,192,193)) {
                long assignment=cloneAssessment(items);
                var eligible=generator.getEligibility(user,assignment,"A4");
                assertEquals(items>=5 && items<=192,eligible.eligible(),eligible.toString());
                if(items==4 || items==193) {assertThrows(Exception.class,()->generator.generate(user,assignment,"A4",audit));continue;}
                var sheet=generator.generate(user,assignment,"A4",audit);
                assertEquals(V3DynamicLayout.pages(items),sheet.totalPages());
                var manifest=mobile.getManifest(user,sheet.assignmentUuid(),sheet.answerSheetUuid());
                assertEquals(items,manifest.pages().stream().mapToInt(p->p.regions().size()).sum());
                assertEquals(items,manifest.pages().stream().flatMap(p->p.regions().stream()).map(r->r.questionUuid()).distinct().count());
                byte[] pdf=generator.getPdf(user,sheet.answerSheetVersionId()).bytes();
                verifyPdf(pdf,manifest,evidence.resolve("boundary-"+items));
                if(items==17 || items==192) exerciseWorkflow(app,user,audit,sheet.answerSheetVersionId(),manifest,pdf);
            }
            for(long assignment:List.of(1005L,1006L)) {
                var eligibility=generator.getEligibility(user,assignment,"A4");assertTrue(eligibility.eligible(),eligibility.toString());
                var sheet=generator.generate(user,assignment,"A4",audit);
                var manifest=mobile.getManifest(user,sheet.assignmentUuid(),sheet.answerSheetUuid());
                assertEquals(assignment==1005?7:10,manifest.totalQuestions());
                assertEquals(2,manifest.manifestVersion());
                byte[] pdf=generator.getPdf(user,sheet.answerSheetVersionId()).bytes();
                verifyPdf(pdf,manifest,evidence.resolve("assignment-"+assignment));
                mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("assignment-"+assignment+"-manifest.json").toFile(),manifest);
                assertTrue(mobile.download(user).answerSheets().stream().anyMatch(s->s.answerSheetUuid().equals(sheet.answerSheetUuid())));
                outputs.add(mapper.valueToTree(sheet));
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("generated-sheets.json").toFile(),outputs);
        }
    }
    private void verifyPdf(byte[] pdf,V3AnswerSheetManifestResponse manifest,Path path) throws Exception {
        Files.write(Path.of(path+".pdf"),pdf);
        try(var document=Loader.loadPDF(pdf)) {
            assertEquals(manifest.totalPages(),document.getNumberOfPages());
            var renderer=new PDFRenderer(document);
            for(int i=0;i<document.getNumberOfPages();i++) {
                var page=manifest.pages().get(i);assertEquals(V3DynamicLayout.CODE,page.template().code());
                assertEquals(3,page.qr().payloadVersion());assertEquals(21,page.templateRegions().size());
                assertEquals(V3DynamicLayout.qr(manifest.answerSheetUuid(),page.pageUuid(),manifest.testAssignment().assignmentUuid(),i+1,manifest.totalPages(),page.pageGeometryHash()),page.qr().payload());
                var image=renderer.renderImageWithDPI(i,150);
                ImageIO.write(image,"png",Path.of(path+"-p"+(i+1)+".png").toFile());
                var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));
                var result=new com.google.zxing.MultiFormatReader().decode(bitmap,Map.of(com.google.zxing.DecodeHintType.TRY_HARDER,true));
                assertEquals(page.qr().payload(),result.getText());
            }
        }
    }
    private void exerciseWorkflow(org.springframework.context.ConfigurableApplicationContext app,V3AuthenticatedUser user,
            V3RequestMetadata audit,long sheetId,V3AnswerSheetManifestResponse manifest,byte[] pdf) throws Exception {
        long membership=jdbc.queryForObject("SELECT MIN(l.class_list_id) FROM class_lists l JOIN class_assignments c ON c.class_id=l.class_id JOIN test_assignments t ON t.class_assignment_id=c.class_assignment_id WHERE t.test_assignment_id=? AND l.enrollment_status='enrolled'",Long.class,manifest.testAssignment().testAssignmentId());
        String resultUuid=uuid(),scanUuid=uuid(),sync=uuid();
        var ingestion=app.getBean(V3ScanPageIngestionService.class);var detections=app.getBean(V3DetectionUploadService.class);
        var verification=app.getBean(V3TeacherVerificationService.class);var scoring=app.getBean(V3ScoringService.class);
        long resultId=0;
        try(var document=Loader.loadPDF(pdf)) {
            for(var page:manifest.pages()) {
                var bytes=new ByteArrayOutputStream();ImageIO.write(new PDFRenderer(document).renderImageWithDPI(page.pageNumber()-1,150),"jpg",bytes);
                byte[] image=bytes.toByteArray();String imageHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(image));
                String scanPageUuid=uuid();
                var request=new V3ScanPageUploadMetadata("3.0",sync,resultUuid,scanUuid,scanPageUuid,manifest.answerSheetUuid(),page.pageUuid(),manifest.testAssignment().assignmentUuid(),membership,page.pageNumber(),1,"3.0.0",page.qr().payloadHash(),imageHash,Instant.now());
                var upload=ingestion.ingest(user,request,new MockMultipartFile("image","scan.jpg","image/jpeg",image));
                assertEquals("created",upload.uploadStatus());
                assertEquals("replayed",ingestion.ingest(user,request,new MockMultipartFile("image","scan.jpg","image/jpeg",image)).uploadStatus());
                resultId=jdbc.queryForObject("SELECT test_result_id FROM test_results WHERE result_uuid=?",Long.class,resultUuid);
                if(manifest.totalQuestions()==17 && page.pageNumber()==2) {
                    long before=jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Long.class,resultId);
                    var rescan=verification.verify(user,new V3VerificationBatch("3.0",uuid(),uuid(),manifest.testAssignment().assignmentUuid(),List.of(new V3VerificationBatch.Item(resultUuid,before,List.of(new V3VerificationBatch.PageDecision(uuid(),scanPageUuid,"rescan_requested","BLUR","Synthetic rescan check",Instant.now())),List.of()))));
                    assertEquals("success",rescan.items().get(0).status());
                    scanPageUuid=uuid();
                    var replacement=new V3ScanPageUploadMetadata("3.0",sync,resultUuid,scanUuid,scanPageUuid,manifest.answerSheetUuid(),page.pageUuid(),manifest.testAssignment().assignmentUuid(),membership,page.pageNumber(),2,"3.0.0",page.qr().payloadHash(),imageHash,Instant.now());
                    assertEquals("created",ingestion.ingest(user,replacement,new MockMultipartFile("image","scan.jpg","image/jpeg",image)).uploadStatus());
                }
                var rows=page.regions().stream().map(r->new V3DetectionBatch.Detection(uuid(),r.regionUuid(),r.questionUuid(),"detected","A",BigDecimal.ONE)).toList();
                detections.upload(user,scanPageUuid,new V3DetectionBatch("3.0",uuid(),uuid(),rows));
                long revision=jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Long.class,resultId);
                String selectedPageUuid=scanPageUuid;
                var answers=rows.stream().map(d->new V3VerificationBatch.Answer(uuid(),uuid(),d.questionUuid(),d.regionUuid(),selectedPageUuid,new V3VerificationBatch.ObjectiveEvaluation("objective",d.detectionUuid()),null,Instant.now())).toList();
                var outcome=verification.verify(user,new V3VerificationBatch("3.0",uuid(),uuid(),manifest.testAssignment().assignmentUuid(),List.of(new V3VerificationBatch.Item(resultUuid,revision,List.of(new V3VerificationBatch.PageDecision(uuid(),scanPageUuid,"accepted",null,null,Instant.now())),answers))));
                assertEquals("success",outcome.items().get(0).status(),outcome.toString());
                if(page.pageNumber()<manifest.totalPages()) {
                    long id=resultId;assertThrows(com.capstone.assessment.v3.auth.exception.V3AuthException.class,()->scoring.finalizeResult(user,id,audit));
                    assertEquals("in_progress",app.getBean(V3MobileReadbackService.class).sync(user,sync).syncStatus());
                }
            }
        }
        var official=scoring.finalizeResult(user,resultId,audit);assertEquals("finalized",official.resultStatus());
        assertEquals(manifest.totalQuestions(),official.itemsEvaluated());
        assertEquals(official.totalScore(),scoring.finalizeResult(user,resultId,audit).totalScore());
        var readback=app.getBean(V3MobileReadbackService.class);
        var read=readback.result(user,resultUuid);
        assertEquals(manifest.totalPages(),read.pages().stream().filter(p->!"superseded".equals(p.pageStatus())).count());
        assertEquals(official.totalScore(),read.officialScore().totalScore());
        assertEquals(official.totalScore(),readback.analytics(user,resultUuid).metrics().totalScore());
        assertEquals("success",readback.sync(user,sync).syncStatus());
    }
    private long cloneAssessment(int count) {
        long test=copy("tests","test_id",1006,Map.of("test_uuid",uuid(),"test_name","Dynamic boundary "+count,"total_items",count,"status","active"));
        long originalPart=jdbc.queryForObject("SELECT MIN(test_part_id) FROM test_parts WHERE test_id=1006",Long.class);
        long part=copy("test_parts","test_part_id",originalPart,Map.of("test_id",test,"number_of_items",count,"part_order",1));
        for(var mapping:jdbc.queryForList("SELECT * FROM part_skill_mappings WHERE test_part_id=? ORDER BY part_skill_mapping_id LIMIT 1",originalPart)) {
            mapping.remove("part_skill_mapping_id");mapping.put("test_part_id",part);
            mapping.put("start_item_number",1);mapping.put("end_item_number",count);mapping.put("item_count",count);
            insert("part_skill_mappings",mapping);
        }
        long original=jdbc.queryForObject("SELECT MIN(question_id) FROM questions WHERE test_part_id=?",Long.class,originalPart);
        for(int i=1;i<=count;i++) {
            long question=copy("questions","question_id",original,Map.of("test_part_id",part,"question_uuid",uuid(),"item_number",i));
            for(var option:jdbc.queryForList("SELECT * FROM question_options WHERE question_id=?",original)) {
                option.remove("question_option_id");option.put("question_id",question);insert("question_options",option);
            }
            long option=jdbc.queryForObject("SELECT question_option_id FROM question_options WHERE question_id=? AND option_key='A'",Long.class,question);
            var key=jdbc.queryForMap("SELECT * FROM answer_keys WHERE question_id=?",original);key.remove("answer_key_id");key.put("question_id",question);key.put("correct_question_option_id",option);insert("answer_keys",key);
        }
        return copy("test_assignments","test_assignment_id",1006,Map.of("test_id",test,"assignment_uuid",uuid(),"allow_late_capture",true));
    }
    private long copy(String table,String pk,long id,Map<String,Object> changes) {
        var row=jdbc.queryForMap("SELECT * FROM "+table+" WHERE "+pk+"=?",id);row.remove(pk);row.putAll(changes);return insert(table,row);
    }
    private long insert(String table,Map<String,Object> row) {
        var keys=new GeneratedKeyHolder();String sql="INSERT INTO "+table+" ("+String.join(",",row.keySet())+") VALUES ("+String.join(",",Collections.nCopies(row.size(),"?"))+")";
        jdbc.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);int i=1;for(var value:row.values())s.setObject(i++,value);return s;},keys);
        return keys.getKey()==null?0:keys.getKey().longValue();
    }
}
