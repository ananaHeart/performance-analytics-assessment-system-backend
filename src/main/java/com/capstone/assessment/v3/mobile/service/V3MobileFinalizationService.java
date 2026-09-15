package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.repository.V3MobileFinalizationRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanUploadLedgerRepository;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage;
import com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ResultContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;

/** Internal bridge; no new endpoint. Must participate in the existing scorer transaction. */
@Service
@Profile("v3")
public class V3MobileFinalizationService {
    private final V3MobileFinalizationRepository repository;
    private final V3ScanUploadLedgerRepository ledger;
    private final V3OriginalScanImageStorage storage;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final V3WrittenFinalizationService written;
    public V3MobileFinalizationService(V3MobileFinalizationRepository repository,V3ScanUploadLedgerRepository ledger,
            V3OriginalScanImageStorage storage,ObjectMapper mapper,
            @Value("${app.v3.mobile.finalization-enabled:false}") boolean enabled,V3WrittenFinalizationService written) {
        this.repository=repository;this.ledger=ledger;this.storage=storage;this.mapper=mapper;this.enabled=enabled;
        this.written=written;
    }
    public void lockOwner(V3AuthenticatedUser user) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Finalization requires a transaction.");
        if(ledger.lockOwner(user.userId(),user.schoolId()).isEmpty())
            throw new V3AuthException("RESULT_ACCESS_DENIED","An active assigned teacher account is required.",HttpStatus.FORBIDDEN);
    }
    public Optional<Prepared> prepare(ResultContext context) {
        if(!repository.hasPaperCapture(context.testResultId())) return Optional.empty();
        if(!enabled) throw new V3AuthException("MOBILE_FINALIZATION_UNAVAILABLE",
                "Scan-backed finalization is awaiting deployment of the Mobile persistence contracts.",HttpStatus.SERVICE_UNAVAILABLE);
        long revision=repository.revision(context.testResultId());
        var saved=repository.receipt(context.testResultId());
        if(saved.isPresent()) {
            var receipt=saved.get();
            if(!"finalized".equals(context.resultStatus()) || receipt.revision()!=revision || receipt.scoreVersion()!=context.scoreVersion())
                throw conflict("FINALIZATION_STATE_CONFLICT","The saved official score requires audited reconciliation.");
            return Optional.of(new Prepared(revision,replayed(officialSnapshot(context,receipt))));
        }
        if((!"draft".equals(context.resultStatus()) && !"pending_verification".equals(context.resultStatus()))
                || context.scoredAt()!=null || context.scoreVersion()!=1)
            throw conflict("AUDITED_REOPEN_REQUIRED","An existing official result cannot be recomputed through first finalization.");
        if(revision>=9007199254740991L) throw conflict("RESULT_REVISION_EXHAUSTED","The result revision cannot advance safely.");
        validateOriginal(context);
        var types=repository.lockQuestionTypes(context.testId());
        if(types.isEmpty() || types.size()>200 || types.stream().anyMatch(t->!java.util.Set.of("multiple_choice","true_false","essay","identification","enumeration").contains(t)))
            throw conflict("ANSWER_COVERAGE_INVALID","The captured assessment must contain supported questions within the Mobile bound.");
        int objectiveCount=(int)types.stream().filter(t->java.util.Set.of("multiple_choice","true_false").contains(t)).count();
        if(!repository.verifiedObjectiveCoverage(context,objectiveCount))
            throw conflict("OBJECTIVE_VERIFICATION_INCOMPLETE","Every objective answer must match an accepted detection and teacher audit.");
        if(repository.invalidObjectiveKeys(context.testId()))
            throw conflict("ANSWER_KEY_INVALID","Each objective key must reference an active option of its own question.");
        if(types.size()>objectiveCount)written.prepare(context,types.size()-objectiveCount,revision);
        return Optional.of(new Prepared(revision,null));
    }
    /** Only the correction validator can construct this transaction-bound proof. No client endpoint accepts it. */
    public Prepared prepareCorrection(ResultContext context,V3ResultCorrectionService.ValidatedCorrection correction) {
        if(!enabled)throw new V3AuthException("MOBILE_FINALIZATION_UNAVAILABLE","Mobile finalization is release gated.",HttpStatus.SERVICE_UNAVAILABLE);
        long revision=repository.revision(context.testResultId());
        var receipt=repository.receipt(context.testResultId()).orElseThrow(()->conflict("FINALIZATION_STATE_CONFLICT","Previous official receipt is missing."));
        if(correction==null || !correction.matches(context,revision,receipt))throw conflict("CORRECTION_NOT_VALIDATED","A current, fully validated correction transaction is required.");
        officialSnapshot(context,receipt);validateOriginal(context);
        if(repository.invalidObjectiveKeys(context.testId()))throw conflict("ANSWER_KEY_INVALID","Objective keys must reference active options of their own question.");
        return new Prepared(revision,null,correction.targetVersion());
    }
    void validateOriginal(ResultContext context) {
        if(!repository.completeSelectedCapture(context) || !repository.validCaptureHistory(context.testResultId()))
            throw conflict("SCAN_VERIFICATION_INCOMPLETE","A complete accepted current sheet capture and teacher page audit are required.");
        var originals=repository.originals(context.testResultId());
        int expectedPages=repository.expectedPages(context.testResultId());
        if(originals.size()!=expectedPages || originals.stream().map(e->e.pageId()).distinct().count()!=expectedPages)
            throw conflict("SCAN_EVIDENCE_INCOMPLETE","Exactly one immutable original is required for every selected page.");
        for (var evidence : originals) {
        var upload=ledger.find(evidence.pageUuid(),true).orElseThrow(()->conflict("SCAN_EVIDENCE_INCOMPLETE","A committed original upload receipt is required."));
        var image=upload.image();
        if(!"committed".equals(upload.state()) || upload.pageId()==null || upload.pageId()!=evidence.pageId()
                || upload.teacherId()!=context.teacherUserId() || !upload.schoolId().equals(context.testSchoolId())
                || !"retained".equals(evidence.purgeStatus()) || evidence.derived()
                || !image.attachmentUuid().equals(evidence.uuid()) || !image.storageProvider().equals(evidence.provider())
                || !image.storageKey().equals(evidence.key()) || !image.mimeType().equals(evidence.mime())
                || image.fileSizeBytes()!=evidence.size() || !image.contentHash().equals(evidence.hash())
                || !image.contentHash().equals(evidence.imageHash()))
            throw conflict("SCAN_EVIDENCE_INCOMPLETE","The selected original does not match its immutable committed receipt.");
        storage.read(image); // Verify every page, including after replay/correction.
        }
    }
    V3ScoredResultResponse officialSnapshot(ResultContext context,V3MobileFinalizationRepository.Receipt receipt) {
        try {
            var r=mapper.readValue(receipt.json(),V3ScoredResultResponse.class);
            if(r==null || r.resultUuid()==null || r.totalScore()==null || r.maxScore()==null || r.percentage()==null || r.performanceStatus()==null || r.scoredAt()==null
                    || r.parts()==null || r.parts().isEmpty() || r.parts().size()>200 || r.parts().stream().anyMatch(java.util.Objects::isNull)
                    || r.testResultId()!=context.testResultId() || !r.resultUuid().equals(context.resultUuid()) || r.testAssignmentId()!=context.testAssignmentId()
                    || r.testId()!=context.testId() || r.classListId()!=context.classListId() || r.studentId()!=context.studentId() || r.attemptNumber()!=context.attemptNumber()
                    || r.scoreVersion()!=context.scoreVersion() || !"finalized".equals(r.resultStatus()) || context.scoredAt()==null
                    || !r.scoredAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).equals(context.scoredAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                    || r.totalScore().compareTo(context.totalScore())!=0 || r.maxScore().compareTo(context.maxScore())!=0 || r.itemsEvaluated()!=context.itemsEvaluated()
                    || context.percentageSnapshot()==null || r.percentage().compareTo(context.percentageSnapshot())!=0 || !r.performanceStatus().equals(context.performanceStatus())
                    || !java.util.Objects.equals(r.performanceRuleSetId(),context.performanceRuleSetId()))throw conflict("FINALIZATION_STATE_CONFLICT","The official score snapshot no longer matches the result.");
            return r;
        }catch(JsonProcessingException e){throw conflict("FINALIZATION_STATE_CONFLICT","The saved official score receipt cannot be read safely.");}
    }
    public void complete(Prepared prepared,V3ScoredResultResponse response) {
        try {
            if(prepared.targetScoreVersion()>0)repository.saveCorrection(response,prepared.revision(),mapper.writeValueAsString(response));
            else repository.save(response.testResultId(),prepared.revision(),response.scoreVersion(),mapper.writeValueAsString(response));
        } catch(JsonProcessingException e) { throw new IllegalStateException("Cannot store finalization receipt.",e); }
    }
    private V3ScoredResultResponse replayed(V3ScoredResultResponse r) {
        return new V3ScoredResultResponse(r.testResultId(),r.resultUuid(),r.testAssignmentId(),r.testId(),r.classListId(),
                r.studentId(),r.studentName(),r.attemptNumber(),r.totalScore(),r.maxScore(),r.percentage(),r.performanceStatus(),
                r.performanceLabel(),r.performanceRuleSetId(),r.itemsEvaluated(),r.scoreVersion(),r.resultStatus(),r.scoredAt(),false,r.parts());
    }
    private V3AuthException conflict(String code,String message) { return new V3AuthException(code,message,HttpStatus.CONFLICT); }
    public record Prepared(long revision,V3ScoredResultResponse replay,int targetScoreVersion) {
        public Prepared(long revision,V3ScoredResultResponse replay){this(revision,replay,0);}
    }
}
