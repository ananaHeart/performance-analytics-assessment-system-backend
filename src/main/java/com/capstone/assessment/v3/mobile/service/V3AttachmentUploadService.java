package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3AttachmentMetadata;
import com.capstone.assessment.v3.mobile.dto.V3AttachmentResponse;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.repository.*;
import com.capstone.assessment.v3.mobile.repository.V3AttachmentUploadRepository.*;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository.Page;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Durable attachment intent followed by file publication and atomic evidence/receipt commit. HTTP is release-gated. */
@Service
@Profile("v3")
public class V3AttachmentUploadService {
    private final V3AttachmentUploadRepository repository;
    private final V3DetectionRepository pages;
    private final V3ScanUploadLedgerRepository owners;
    private final V3OriginalScanImageStorage storage;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public V3AttachmentUploadService(V3AttachmentUploadRepository repository,V3DetectionRepository pages,
            V3ScanUploadLedgerRepository owners,V3OriginalScanImageStorage storage,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.repository=repository;this.pages=pages;this.owners=owners;this.storage=storage;this.mapper=mapper.copy();
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public V3AttachmentResponse upload(V3AuthenticatedUser user,V3AttachmentMetadata request,MultipartFile file) {
        if(user==null || !"teacher".equals(user.role()) || !"active".equals(user.status()) || user.schoolId()==null || user.schoolId().isBlank())
            throw error("TEACHER_REQUIRED","An active assigned teacher is required.",HttpStatus.FORBIDDEN);
        validate(request);
        String json=json(request),hash=hash(json);
        // Authorize before reading client bytes or returning an existing identity.
        transaction.execute(s->{owner(user);var p=page(user,request,true);var intent=repository.intent(request.operationUuid()).orElse(null);
            if(intent!=null) identity(user,p,intent,hash);else mutable(p);return null;});
        try(var staged=storage.stageEvidence(file,request.contentHash(),request.mimeType())) {
            if(staged.image().fileSizeBytes()!=request.fileSizeBytes()) throw invalid("fileSizeBytes does not match received bytes.");
            transaction.execute(s->{
                owner(user);var p=page(user,request,true);var old=repository.intent(request.operationUuid()).orElse(null);
                if(old!=null) { identity(user,p,old,hash);return null; }
                p=page(user,request,false);mutable(p);lineage(p,request);
                if(repository.attachmentOccupied(request.attachmentUuid()) || pages.syncExists(request.syncUuid())) throw conflict();
                repository.reserve(user,p,request,hash,json,staged.image());
                // Keep bytes if the database commit acknowledgement is uncertain.
                staged.retainForRecovery();return null;
            });
            try {
                return transaction.execute(s->{
                    owner(user);var p=page(user,request,true);
                    var intent=repository.intent(request.operationUuid()).orElseThrow(this::conflict);identity(user,p,intent,hash);
                    if("committed".equals(intent.state())) {
                        var evidence=retained(p,request.attachmentUuid());
                        if(!Objects.equals(intent.attachmentId(),evidence.id()) || !intent.image().equals(evidence.image())) throw conflict();
                        verifyBytes(evidence);
                        var receipt=parse(intent.response());
                        return new V3AttachmentResponse(receipt.attachmentUuid(),receipt.backendAttachmentId(),receipt.contentHash(),"replayed",receipt.acknowledgedAt());
                    }
                    p=page(user,request,false);mutable(p);var lineage=lineage(p,request);
                    storage.restorePending(intent.image(),staged);storage.recoverPublish(intent.image());
                    long id=repository.insertEvidence(p,request,intent.image(),lineage.region(),lineage.source(),request.crop()==null?null:json(request.crop()));
                    if(id>9007199254740991L) throw conflict();
                    var response=new V3AttachmentResponse(request.attachmentUuid(),id,request.contentHash(),"created",Instant.now());
                    repository.committed(p,request.operationUuid(),intent.syncId(),id,json(response));return response;
                });
            } catch(RuntimeException failure) {
                // An uncertain successful commit is not overwritten; only still-pending intents get retry state.
                try { transaction.execute(s->{owner(user);page(user,request,true);var i=repository.intent(request.operationUuid()).orElse(null);
                    if(i!=null && i.teacherId()==user.userId() && i.hash().equals(hash)) repository.failed(request.operationUuid(),
                        failure instanceof V3AuthException a?a.getCode():"ATTACHMENT_STORAGE_UNAVAILABLE");return null;}); }
                catch(RuntimeException ignored) { /* The durable intent remains available for exact retry. */ }
                throw failure;
            }
        } catch(DuplicateKeyException e) { throw conflict(); }
        catch(DataAccessException | TransactionException e) {
            throw error("ATTACHMENT_STORAGE_UNAVAILABLE","Attachment persistence requires retry with the same identifiers and bytes.",HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
    /** Caller owns and locks the result/page in the same write transaction. Revalidate full retained lineage before linking an answer. */
    public long evidenceForScoring(Page page,String uuid,long region) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Evidence scoring checks require a transaction.");
        var evidence=retained(page,uuid);
        if(!List.of("answer_crop","teacher_evidence").contains(evidence.type())
                || evidence.regionId()!=null && evidence.regionId()!=region) throw lineageError();
        V3AttachmentMetadata metadata;
        try { metadata=mapper.readValue(repository.committedRequest(uuid).orElseThrow(this::lineageError),V3AttachmentMetadata.class); }
        catch(java.io.IOException e) { throw lineageError(); }
        if(!uuid.equals(metadata.attachmentUuid()) || !page.resultUuid().equals(metadata.resultUuid())
                || !metadata.attachmentType().equals(evidence.type()) || !metadata.contentHash().equals(evidence.image().contentHash())
                || metadata.fileSizeBytes()!=evidence.image().fileSizeBytes() || !metadata.mimeType().equals(evidence.image().mimeType())) throw lineageError();
        var derived=lineage(page,metadata);
        if(!Objects.equals(derived.region(),evidence.regionId()) || !Objects.equals(derived.source(),evidence.sourceId())) throw lineageError();
        verifyBytes(evidence);return evidence.id();
    }
    private Lineage lineage(Page p,V3AttachmentMetadata r) {
        Long region=r.regionUuid()==null?null:repository.region(p,r.regionUuid()).orElseThrow(()->invalid("Region does not belong to the immutable question page."));
        var original=retained(p,repository.originalUuid(p));
        if(!"original_page".equals(original.type()) || original.regionId()!=null || original.sourceId()!=null) throw lineageError();
        verifyBytes(original);
        if("normalized_page".equals(r.attachmentType())) {
            var source=retained(p,r.sourceAttachmentUuid());
            if(source.id()!=original.id()) throw lineageError();
            return new Lineage(region,source.id());
        }
        if(r.crop()!=null) {
            var base=retained(p,r.crop().baseAttachmentUuid());
            if("normalized_page".equals(base.type())) {
                if(!Objects.equals(base.sourceId(),original.id()) || base.regionId()!=null) throw lineageError();
            } else if(base.id()!=original.id()) throw lineageError();
            verifyBytes(base);var c=r.crop();
            if(c.baseWidthPx()!=base.image().width() || c.baseHeightPx()!=base.image().height()
                    || c.width()>c.baseWidthPx() || c.height()>c.baseHeightPx()
                    || c.x()>c.baseWidthPx()-c.width() || c.y()>c.baseHeightPx()-c.height()) throw lineageError();
        }
        return new Lineage(region,null);
    }
    private Evidence retained(Page p,String uuid) {
        var e=repository.evidence(p,uuid).orElseThrow(this::lineageError);
        if(!e.committed() || !"retained".equals(e.purge()) || !"local".equals(e.image().storageProvider())) throw lineageError();
        return e;
    }
    private void verifyBytes(Evidence evidence) {
        byte[] bytes=storage.read(evidence.image());
        // Dimensions are checked against actual retained bytes, not the client crop declaration or a mutable DB value alone.
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=javax.imageio.ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw lineageError();var reader=readers.next();
            try { reader.setInput(input);if(reader.getWidth(0)!=evidence.image().width() || reader.getHeight(0)!=evidence.image().height()) throw lineageError(); }
            finally { reader.dispose(); }
        } catch(java.io.IOException e) { throw lineageError(); }
    }
    private void owner(V3AuthenticatedUser u) { if(owners.lockOwner(u.userId(),u.schoolId()).isEmpty()) throw error("TEACHER_REQUIRED","Teacher access is no longer active.",HttpStatus.FORBIDDEN); }
    private Page page(V3AuthenticatedUser u,V3AttachmentMetadata r,boolean replay) {
        var p=pages.lockPage(u,r.scanPageUuid(),replay).orElseThrow(()->error("RESOURCE_NOT_FOUND","Owned scan page was not found.",HttpStatus.NOT_FOUND));
        if(!p.resultUuid().equals(r.resultUuid())) throw error("RESOURCE_NOT_FOUND","Owned result/page pair was not found.",HttpStatus.NOT_FOUND);
        return p;
    }
    private void mutable(Page p) {
        if(!List.of("draft","pending_verification").contains(p.resultStatus()) || !"selected".equals(p.linkStatus())
                || !List.of("captured","accepted").contains(p.pageStatus()) || List.of("rejected","rescan_requested").contains(p.scanStatus())
                || p.sheetVersion()!=p.testVersion() || p.revision()>=9007199254740991L)
            throw error("ATTACHMENT_STATE_CONFLICT","Evidence cannot be added to this result/page state.",HttpStatus.CONFLICT);
    }
    private void identity(V3AuthenticatedUser u,Page p,Intent i,String hash) {
        if(i.teacherId()!=u.userId() || i.pageId()!=p.pageId() || !i.hash().equals(hash)) throw conflict();
    }
    private void validate(V3AttachmentMetadata r) {
        if(r==null || !"3.0".equals(r.contractVersion()) || !uuid(r.syncUuid()) || !uuid(r.operationUuid()) || !uuid(r.attachmentUuid())
                || !uuid(r.resultUuid()) || !uuid(r.scanPageUuid()) || (r.regionUuid()!=null&&!uuid(r.regionUuid()))
                || (r.sourceAttachmentUuid()!=null&&!uuid(r.sourceAttachmentUuid())) || r.contentHash()==null || !r.contentHash().matches("[0-9a-f]{64}")
                || r.fileSizeBytes()<1 || r.fileSizeBytes()>V3OriginalScanImageStorage.MAX_FILE_BYTES
                || !("image/jpeg".equals(r.mimeType())||"image/png".equals(r.mimeType())) || r.capturedAt()==null
                || r.capturedAt().getEpochSecond()<1 || r.capturedAt().getEpochSecond()>2147483647L
                || !("normalized_page".equals(r.attachmentType())||"answer_crop".equals(r.attachmentType())||"teacher_evidence".equals(r.attachmentType()))) throw invalid("Unsupported attachment metadata.");
        if("normalized_page".equals(r.attachmentType())) {
            if(r.sourceAttachmentUuid()==null || r.regionUuid()!=null || r.crop()!=null || r.attachmentUuid().equals(r.sourceAttachmentUuid())) throw lineageError();
        } else if(r.sourceAttachmentUuid()!=null) throw lineageError();
        if("answer_crop".equals(r.attachmentType())) {
            var c=r.crop();
            if(r.regionUuid()==null || c==null || !uuid(c.baseAttachmentUuid()) || r.attachmentUuid().equals(c.baseAttachmentUuid())
                    || !"image_pixels_top_left".equals(c.coordinateSpace()) || c.baseWidthPx()<1 || c.baseWidthPx()>40000000
                    || c.baseHeightPx()<1 || c.baseHeightPx()>40000000 || c.x()<0 || c.y()<0 || c.width()<1 || c.height()<1
                    || c.width()>c.baseWidthPx() || c.height()>c.baseHeightPx() || c.x()>c.baseWidthPx()-c.width() || c.y()>c.baseHeightPx()-c.height()) throw lineageError();
        } else if(r.crop()!=null) throw lineageError();
    }
    private boolean uuid(String s) { return s!=null&&s.matches(V3ScanPageUploadMetadata.UUID_PATTERN); }
    private String json(Object value) { try{return mapper.writeValueAsString(value);}catch(java.io.IOException e){throw invalid("Attachment metadata cannot be serialized.");} }
    private String hash(String value) { try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    private V3AttachmentResponse parse(String value) { try{return mapper.readValue(value,V3AttachmentResponse.class);}catch(java.io.IOException e){throw conflict();} }
    private V3AuthException conflict() { return error("ATTACHMENT_IDENTITY_CONFLICT","Attachment, operation or sync identity is already bound to different content.",HttpStatus.CONFLICT); }
    private V3AuthException lineageError() { return error("ATTACHMENT_LINEAGE_INVALID","Evidence requires a retained committed base in the same page and valid crop bounds.",HttpStatus.UNPROCESSABLE_ENTITY); }
    private V3AuthException invalid(String m) { return error("VALIDATION_FAILED",m,HttpStatus.UNPROCESSABLE_ENTITY); }
    private V3AuthException error(String code,String message,HttpStatus status) { return new V3AuthException(code,message,status); }
    private record Lineage(Long region,Long source) { }
}
