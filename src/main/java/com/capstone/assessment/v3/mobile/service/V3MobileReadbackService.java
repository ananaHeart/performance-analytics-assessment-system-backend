package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse.IdMapping;
import com.capstone.assessment.v3.mobile.dto.V3MobileReadback.*;
import com.capstone.assessment.v3.mobile.dto.V3VerificationResponse.Error;
import com.capstone.assessment.v3.mobile.repository.V3MobileReadbackRepository;
import com.capstone.assessment.v3.mobile.repository.V3MobileReadbackRepository.*;
import com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;

@Service
@Profile("v3")
public class V3MobileReadbackService {
    private static final List<String> UNAVAILABLE_MODULES=List.of("itemAnalysis","mastery","interventions","student360");
    private final V3MobileReadbackRepository repository;
    private final ObjectMapper mapper;
    private final TransactionTemplate snapshot;
    private final boolean enabled;
    public V3MobileReadbackService(V3MobileReadbackRepository repository,ObjectMapper mapper,PlatformTransactionManager manager,
            @Value("${app.v3.mobile.readback-enabled:false}") boolean enabled) {
        this.repository=repository;this.mapper=mapper;this.enabled=enabled;
        snapshot=new TransactionTemplate(manager);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setReadOnly(true);
    }
    public Result result(V3AuthenticatedUser user,String uuid) {
        return read(user,uuid,()-> {
            var row=ownedResult(user,uuid);var score=score(row);var pages=bounded(repository.pages(row.id()));
            boolean dynamic=repository.dynamicResult(row.id());
            var mappings=new TreeMap<String,IdMapping>();
            add(mappings,new IdMapping("test_result",row.uuid(),row.id()));
            var states=new ArrayList<Page>();var selected=new ArrayList<PageRow>();var seenPages=new HashSet<String>();
            for(var page:pages) {
                if(page.attachmentId()==null || page.attachmentUuid()==null || !seenPages.add(page.pageUuid())) throw inconsistent();
                states.add(new Page(page.scanUuid(),page.pageUuid(),page.manifestUuid(),page.status(),page.attachmentUuid()));
                if("selected".equals(page.linkStatus()) && !"superseded".equals(page.status())) selected.add(page);
                add(mappings,new IdMapping("scan_session",page.scanUuid(),page.scanId()));
                add(mappings,new IdMapping("scan_page",page.pageUuid(),page.pageId()));
                add(mappings,new IdMapping("answer_sheet_version",page.sheetUuid(),page.sheetId()));
                add(mappings,new IdMapping("answer_sheet_page",page.manifestUuid(),page.manifestId()));
                add(mappings,new IdMapping("answer_attachment",page.attachmentUuid(),page.attachmentId()));
            }
            for(var mapping:boundedMappings(repository.mappings(row.id()),dynamic)) add(mappings,mapping);
            var pending=new ArrayList<String>();
            if(!"finalized".equals(row.status()) && !"superseded".equals(row.status())) {
                if(selected.isEmpty()) pending.add("SCAN_CAPTURE_PENDING");
                else if(selected.stream().anyMatch(p->!"accepted".equals(p.status()))) pending.add("PAGE_VERIFICATION_PENDING");
                if(repository.missingAnswers(row)>0) pending.add("ANSWER_VERIFICATION_PENDING");
                pending.add("FINALIZATION_PENDING");
            }
            if(!"ready".equals(score.state().status()) && !"pending".equals(score.state().status())) pending.add(score.state().reasonCode());
            return new Result("3.0",row.uuid(),row.id(),row.assignmentUuid(),row.classListId(),row.studentId(),row.status(),row.revision(),
                    row.scoreVersion(),List.copyOf(pending),boundedMappings(new ArrayList<>(mappings.values()),dynamic),List.copyOf(states),
                    bounded(repository.rubricMappings(row.id())),score.official(),score.state());
        });
    }
    public Analytics analytics(V3AuthenticatedUser user,String uuid) {
        return read(user,uuid,()-> {
            var row=ownedResult(user,uuid);var score=score(row);var s=score.state();var official=score.official();
            return new Analytics("3.0",row.uuid(),s.scoreVersion(),s.status(),s.reasonCode(),official==null?null:official.scoredAt(),
                    official==null?null:new Metrics(official.totalScore(),official.maxScore(),official.percentage(),null,null,null,null),UNAVAILABLE_MODULES);
        });
    }
    public Sync sync(V3AuthenticatedUser user,String uuid) {
        return read(user,uuid,()-> {
            var sync=repository.sync(user,uuid).orElseThrow(this::notFound);
            var intents=bounded(repository.intents(uuid));
            if(!intents.isEmpty()) return scanSync(user,sync,intents);
            var batch=repository.verification(sync.id());var detection=repository.detectionPage(sync.id());
            var attachment=batch.isEmpty() && detection.isEmpty()?repository.attachment(sync.id()):java.util.Optional.<AttachmentStage>empty();
            if(batch.isEmpty() && detection.isEmpty() && attachment.isEmpty()) {
                var reopen=repository.reopen(sync.id());
                if(reopen.isPresent()) {
                    var stage=reopen.get();var result=ownedResult(user,stage.resultUuid());
                    if(result.assignmentId()!=sync.assignmentId())throw notFound();
                    var request=parse(stage.request(),V3ReopenRequest.class);var ack=parse(stage.response(),V3LifecycleAck.class);
                    var stored=repository.items(sync.id());
                    if(!uuid.equals(request.syncUuid()) || !stage.resultUuid().equals(ack.resultUuid())
                            || !"pending_verification".equals(ack.resultStatus()) || !"created".equals(ack.disposition())
                            || ack.replacementResultUuid()!=null || ack.acknowledgedAt()==null
                            || ack.revision()!=stage.revision() || ack.scoreVersion()!=stage.version()
                            || request.expectedRevision()==null || request.expectedScoreVersion()==null
                            || ack.revision()!=request.expectedRevision()+1 || ack.scoreVersion()!=request.expectedScoreVersion()
                            || stored.size()!=1 || !stage.resultUuid().equals(stored.get(0).resultUuid()) || !"success".equals(stored.get(0).status()))throw inconsistent();
                    return new Sync("3.0",uuid,sync.assignmentUuid(),"success",List.of(new SyncItem(stage.resultUuid(),"success",null,List.of())));
                }
                var correction=repository.correction(sync.id());
                if(correction.isPresent()){
                    var stage=correction.get();var result=ownedResult(user,stage.resultUuid());if(result.assignmentId()!=sync.assignmentId())throw notFound();
                    var official=parse(stage.response(),V3ScoredResultResponse.class);var stored=repository.items(sync.id());
                    if(!stage.resultUuid().equals(official.resultUuid()) || official.testResultId()!=result.id() || official.scoreVersion()!=stage.version()
                            || !"finalized".equals(official.resultStatus()) || official.scoredAt()==null || official.totalScore()==null || official.maxScore()==null
                            || stored.size()!=1 || !stage.resultUuid().equals(stored.get(0).resultUuid()) || !"success".equals(stored.get(0).status()))throw inconsistent();
                    return new Sync("3.0",uuid,sync.assignmentUuid(),"success",List.of(new SyncItem(stage.resultUuid(),"success",null,List.of())));
                }
                var supersede=repository.supersede(sync.id());
                if(supersede.isPresent()){
                    var stage=supersede.get();var result=ownedResult(user,stage.resultUuid());var replacement=ownedResult(user,stage.replacementUuid());
                    if(result.assignmentId()!=sync.assignmentId() || replacement.assignmentId()!=result.assignmentId() || replacement.classListId()!=result.classListId())throw notFound();
                    var request=parse(stage.request(),V3SupersedeRequest.class);var ack=parse(stage.response(),V3LifecycleAck.class);var stored=repository.items(sync.id());
                    if(!uuid.equals(request.syncUuid()) || !stage.resultUuid().equals(ack.resultUuid()) || !stage.replacementUuid().equals(ack.replacementResultUuid())
                            || !stage.replacementUuid().equals(request.replacementResultUuid()) || !"superseded".equals(ack.resultStatus()) || !"created".equals(ack.disposition())
                            || ack.acknowledgedAt()==null || ack.revision()!=stage.revision() || ack.scoreVersion()!=stage.version()
                            || request.expectedRevision()==null || request.expectedScoreVersion()==null || ack.revision()!=request.expectedRevision()+1 || ack.scoreVersion()!=request.expectedScoreVersion()
                            || stored.size()!=1 || !stage.resultUuid().equals(stored.get(0).resultUuid()) || !"success".equals(stored.get(0).status()))throw inconsistent();
                    return new Sync("3.0",uuid,sync.assignmentUuid(),"success",List.of(new SyncItem(stage.resultUuid(),"success",null,List.of())));
                }
                throw new V3AuthException("SYNC_STAGE_UNSUPPORTED","This sync has no supported Mobile stage receipt.",HttpStatus.CONFLICT);
            }
            var written=batch.filter(j->"3.1".equals(parse(j,com.fasterxml.jackson.databind.JsonNode.class).path("contractVersion").asText()))
                    .map(j->parse(j,V3WrittenVerificationBatch.class)).orElse(null);
            var verification=written==null?batch.map(j->parse(j,V3VerificationBatch.class)).orElse(null):null;
            var items=new ArrayList<SyncItem>();
            for(var stored:bounded(repository.items(sync.id()))) {
                var result=ownedResult(user,stored.resultUuid());
                if(result.assignmentId()!=sync.assignmentId()) throw notFound();
                var pageIds=new TreeSet<String>();Error error=null;String status;
                if(verification!=null || written!=null) {
                    if(written!=null) {
                        var submitted=written.items().stream().filter(i->i.resultUuid().equals(stored.resultUuid())).findFirst().orElseThrow(this::inconsistent);
                        submitted.pageDecisions().forEach(p->pageIds.add(p.scanPageUuid()));submitted.answers().forEach(a->pageIds.add(a.scanPageUuid()));
                    } else {
                        var submitted=verification.items().stream().filter(i->i.resultUuid().equals(stored.resultUuid())).findFirst().orElseThrow(this::inconsistent);
                        submitted.pageDecisions().forEach(p->pageIds.add(p.scanPageUuid()));submitted.answers().forEach(a->pageIds.add(a.scanPageUuid()));
                    }
                    if(stored.response()==null) status="pending";
                    else {
                        var receipt=parse(stored.response(),V3VerificationResponse.Outcome.class);
                        if(!stored.resultUuid().equals(receipt.resultUuid())) throw inconsistent();
                        status=receipt.status();error=receipt.error();
                    }
                } else {
                    if(attachment.isPresent()) {
                        var stage=attachment.get();pageIds.add(stage.page());
                        status="committed".equals(stage.state())?"success":stage.error()==null?"pending":"failed";
                        error=stage.error()==null?null:new Error(stage.error(),"Attachment upload requires retry or backend review.",
                            List.of("ATTACHMENT_STORAGE_UNAVAILABLE","SCAN_EVIDENCE_NOT_FOUND","SCAN_EVIDENCE_STORAGE_FAILED").contains(stage.error()));
                    } else { pageIds.add(detection.orElseThrow());status=stored.status(); }
                    if(attachment.isEmpty() && !"success".equals(status)) throw inconsistent(); // Detection commits atomically.
                }
                var allowed=repository.pages(result.id()).stream().map(PageRow::pageUuid).collect(java.util.stream.Collectors.toSet());
                if(!allowed.containsAll(pageIds)) throw notFound();
                List<PageOutcome> outcomes=new ArrayList<>();
                for(String page:pageIds) outcomes.add(new PageOutcome(page,status,error));
                items.add(new SyncItem(stored.resultUuid(),status,error,bounded(outcomes)));
            }
            return new Sync("3.0",uuid,sync.assignmentUuid(),aggregate(items.stream().map(SyncItem::status).toList()),List.copyOf(items));
        });
    }
    private Sync scanSync(V3AuthenticatedUser user,SyncRow sync,List<Intent> intents) {
        var groups=new TreeMap<String,List<PageOutcome>>();var expected=new HashMap<String,Integer>();
        var scanIdentities=new HashMap<String,String>();
        for(var intent:intents) {
            var request=parse(intent.json(),V3ScanPageUploadMetadata.class);
            if(!request.syncUuid().equals(sync.uuid()) || !request.assignmentUuid().equals(sync.assignmentUuid())
                    || !request.scanPageUuid().equals(intent.pageUuid()) || !repository.membership(user,sync,request.classListId())) throw notFound();
            int count=repository.expectedPages(sync,request.answerSheetUuid());if(count<1) throw notFound();
            if(repository.resultExists(request.resultUuid())) {
                var existing=ownedResult(user,request.resultUuid());
                if(existing.assignmentId()!=sync.assignmentId() || existing.classListId()!=request.classListId()) throw notFound();
            }
            String identity=request.scanUuid()+":"+request.answerSheetUuid();
            if(scanIdentities.putIfAbsent(request.resultUuid(),identity)!=null && !scanIdentities.get(request.resultUuid()).equals(identity)) throw inconsistent();
            expected.put(request.resultUuid(),count);
            String status="committed".equals(intent.state())?"success":intent.error()==null?"pending":"failed";
            Error error=intent.error()==null?null:new Error(intent.error(),"Original upload requires retry or backend recovery.",true);
            if("success".equals(status)) {
                var result=ownedResult(user,request.resultUuid());
                if(result.assignmentId()!=sync.assignmentId() || result.classListId()!=request.classListId()) throw notFound();
                if(repository.pages(result.id()).stream().noneMatch(p->p.pageUuid().equals(intent.pageUuid()) && p.manifestUuid().equals(request.pageUuid()))) throw inconsistent();
                error=null;
            }
            groups.computeIfAbsent(request.resultUuid(),k->new ArrayList<>()).add(new PageOutcome(intent.pageUuid(),status,error));
        }
        var items=new ArrayList<SyncItem>();
        for(var group:groups.entrySet()) {
            var pages=bounded(group.getValue());String stage=aggregate(pages.stream().map(PageOutcome::status).toList());
            String scanUuid=scanIdentities.get(group.getKey()).split(":",2)[0];
            boolean complete=repository.committedManifestPages(scanUuid)==expected.get(group.getKey());
            String status="success".equals(stage)&&complete?"success":pages.stream().anyMatch(p->"failed".equals(p.status()))?"failed":"pending";
            Error error=pages.stream().map(PageOutcome::error).filter(Objects::nonNull).findFirst().orElse(null);
            if(!complete && error==null) error=new Error("MANIFEST_PAGES_PENDING","The scan does not have committed captures for every manifest page.",true);
            items.add(new SyncItem(group.getKey(),status,error,pages));
        }
        String status=aggregate(items.stream().map(SyncItem::status).toList());
        // A single result can itself have a mixture of committed and failed page receipts.
        if("failed".equals(status) && items.stream().flatMap(i->i.pageOutcomes().stream()).anyMatch(p->"success".equals(p.status()))) status="partial_success";
        return new Sync("3.0",sync.uuid(),sync.assignmentUuid(),status,bounded(items));
    }
    private Score score(ResultRow row) {
        if("superseded".equals(row.status())) return new Score(null,new AnalyticsState("stale",row.scoreVersion(),"RESULT_SUPERSEDED"));
        if("pending_verification".equals(row.status()) && row.receiptJson()!=null && repository.reopened(row))
            return new Score(null,new AnalyticsState("stale",row.scoreVersion(),"RESULT_REOPENED"));
        if(!"finalized".equals(row.status())) return row.receiptJson()!=null || row.scoredAt()!=null
                ?new Score(null,new AnalyticsState("stale",row.scoreVersion(),"RESULT_NOT_FINALIZED"))
                :new Score(null,new AnalyticsState("pending",null,"RESULT_NOT_FINALIZED"));
        if(row.receiptJson()==null) return new Score(null,new AnalyticsState("unavailable",row.scoreVersion(),"OFFICIAL_SCORE_UNAVAILABLE"));
        var r=parse(row.receiptJson(),V3ScoredResultResponse.class);
        if(r.resultUuid()==null || r.totalScore()==null || r.maxScore()==null || r.percentage()==null || r.scoredAt()==null
                || r.studentName()==null || r.studentName().isBlank() || r.performanceStatus()==null || r.performanceLabel()==null
                || r.parts()==null || r.parts().isEmpty() || r.parts().size()>200 || r.parts().stream().anyMatch(Objects::isNull)) throw inconsistent();
        if(!Objects.equals(row.receiptRevision(),row.revision()) || !Objects.equals(row.receiptVersion(),row.scoreVersion())
                || r.testResultId()!=row.id() || !r.resultUuid().equals(row.uuid()) || r.testAssignmentId()!=row.assignmentId()
                || r.testId()!=row.testId() || r.attemptNumber()!=row.attemptNumber()
                || r.classListId()!=row.classListId() || r.studentId()!=row.studentId() || r.scoreVersion()!=row.scoreVersion()
                || !"finalized".equals(r.resultStatus()) || r.totalScore().compareTo(row.total())!=0 || r.maxScore().compareTo(row.max())!=0
                || row.percentage()==null || r.percentage().compareTo(row.percentage())!=0 || r.itemsEvaluated()!=row.items()
                || !Objects.equals(r.performanceRuleSetId(),row.ruleId()) || !Objects.equals(r.performanceStatus(),row.performance()))
            return new Score(null,new AnalyticsState("stale",row.scoreVersion(),"SCORE_SNAPSHOT_MISMATCH"));
        var official=new V3ScoredResultResponse(r.testResultId(),r.resultUuid(),r.testAssignmentId(),r.testId(),r.classListId(),r.studentId(),
                r.studentName(),r.attemptNumber(),r.totalScore(),r.maxScore(),r.percentage(),r.performanceStatus(),r.performanceLabel(),
                r.performanceRuleSetId(),r.itemsEvaluated(),r.scoreVersion(),r.resultStatus(),r.scoredAt(),false,r.parts());
        return new Score(official,new AnalyticsState("ready",row.scoreVersion(),null));
    }
    private <T> T read(V3AuthenticatedUser user,String uuid,Supplier<T> work) {
        if(user==null) throw new V3AuthException("AUTHENTICATION_REQUIRED","Authentication is required.",HttpStatus.UNAUTHORIZED);
        if(!"teacher".equals(user.role()) || !"active".equals(user.status()) || user.schoolId()==null || user.schoolId().isBlank())
            throw new V3AuthException("RESULT_ACCESS_DENIED","An active school teacher is required.",HttpStatus.FORBIDDEN);
        if(uuid==null || !uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new V3AuthException("VALIDATION_FAILED","A canonical UUID is required.",HttpStatus.UNPROCESSABLE_ENTITY);
        if(!enabled) throw new V3AuthException("MOBILE_READBACK_UNAVAILABLE","Mobile readback awaits deployment of its persistence contracts.",HttpStatus.SERVICE_UNAVAILABLE);
        return snapshot.execute(s->{if(!repository.active(user)) throw new V3AuthException("RESULT_ACCESS_DENIED","The teacher account is no longer active in this school.",HttpStatus.FORBIDDEN);return work.get();});
    }
    private ResultRow ownedResult(V3AuthenticatedUser u,String uuid) { return repository.result(u,uuid).orElseThrow(this::notFound); }
    private <T> T parse(String json,Class<T> type) { try { var value=mapper.readValue(json,type);if(value==null)throw inconsistent();return value; } catch(JsonProcessingException e) { throw inconsistent(); } }
    private <T> List<T> boundedMappings(List<T> rows,boolean dynamic) {
        int maximum=dynamic ? 4*com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.MAX_QUESTIONS
                +5*com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.MAX_PAGES*5+3 : 200;
        if(rows.size()>maximum) throw new V3AuthException("READBACK_LIMIT_EXCEEDED","Result identity mappings exceed the supported page capacity.",HttpStatus.CONFLICT);
        return List.copyOf(rows);
    }
    private <T> List<T> bounded(List<T> rows) {
        if(rows.size()>200) throw new V3AuthException("READBACK_LIMIT_EXCEEDED","Readback exceeds the current bounded contract; backend review is required.",HttpStatus.CONFLICT);
        return List.copyOf(rows);
    }
    private void add(Map<String,IdMapping> map,IdMapping item) {
        if(item.uuid()==null || !item.uuid().matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || item.centralId()<1 || item.centralId()>9007199254740991L) throw inconsistent();
        var old=map.putIfAbsent(item.entityType()+":"+item.uuid(),item);
        if(old!=null && old.centralId()!=item.centralId()) throw inconsistent();
    }
    private String aggregate(List<String> states) {
        if(states.isEmpty()) return "pending";
        if(states.contains("pending")) return "in_progress";
        if(states.stream().allMatch("success"::equals)) return "success";
        return states.contains("success")?"partial_success":"failed";
    }
    private V3AuthException notFound() { return new V3AuthException("RESOURCE_NOT_FOUND","The owned Mobile resource was not found.",HttpStatus.NOT_FOUND); }
    private V3AuthException inconsistent() { return new V3AuthException("READBACK_STATE_INCONSISTENT","Stored Mobile state requires backend reconciliation.",HttpStatus.CONFLICT); }
    private record Score(V3ScoredResultResponse official,AnalyticsState state) { }
}
