package com.stocket.attachment.internal.domain;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import com.stocket.attachment.AttachmentSummary;
import com.stocket.attachment.internal.storage.AttachmentStore;
import com.stocket.attachment.internal.storage.StoredObject;
import com.stocket.attachment.internal.validation.AttachmentValidationException;
import com.stocket.attachment.internal.validation.AttachmentValidator;
import com.stocket.catalog.CatalogInventoryQuery;
import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.identity.IdentityRole;
import com.stocket.inventory.InventoryQuery;
import com.stocket.audit.AuditEvent;
import com.stocket.identity.RequestContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AttachmentService {
    private final AttachmentRepository repository; private final AttachmentStore store;
    private final AttachmentValidator validator; private final CurrentHouseholdProvider current; private final ApplicationEventPublisher events;
    private final CatalogInventoryQuery catalog; private final InventoryQuery inventory; private final TransactionTemplate transactions;
    public AttachmentService(AttachmentRepository repository, AttachmentStore store, AttachmentValidator validator,
                             CurrentHouseholdProvider current, CatalogInventoryQuery catalog, InventoryQuery inventory, ApplicationEventPublisher events,
                             TransactionTemplate transactions) {
        this.repository=repository; this.store=store; this.validator=validator; this.current=current;
        this.catalog=catalog; this.inventory=inventory; this.events=events; this.transactions=transactions;
    }

    public AttachmentSummary upload(String ownerType, UUID ownerId, String purposeValue, String filename,
                                    String declaredType, InputStream input, String requestId) throws IOException {
        CurrentHousehold context=current.requireCurrent(); requireWriter(context); validateOwner(context.householdId(), ownerType, ownerId);
        AttachmentPurpose purpose=parsePurpose(purposeValue); StoredObject staged=store.stage(input);
        try {
            var valid=validator.validate(staged.stagingPath(), filename, declaredType, staged.sizeBytes(), staged.sha256());
            Attachment attachment=new Attachment(UUID.randomUUID(), context.householdId(), ownerType, ownerId, purpose,
                    valid.safeFilename(), staged.storageKey(), valid.detectedMediaType(), valid.sizeBytes(), valid.sha256(),
                    context.accountId(), requestId);
            try { transactions.executeWithoutResult(status -> repository.saveAndFlush(attachment)); }
            catch (RuntimeException error) { store.discard(staged); throw error; }
            store.commit(staged);
            transactions.executeWithoutResult(status -> {
                if (purpose==AttachmentPurpose.COVER_IMAGE) {
                    for (Attachment old : repository.findByHouseholdIdAndOwnerTypeAndOwnerIdAndPurposeAndStatus(
                            context.householdId(), ownerType, ownerId, purpose, AttachmentStatus.AVAILABLE)) old.deleted();
                    repository.flush();
                }
                attachment.available(); repository.saveAndFlush(attachment);
            });
            events.publishEvent(new AuditEvent(UUID.randomUUID(), context.householdId(), Instant.now(), "AttachmentUploaded",
                    "SUCCESS", context.accountId(), "ATTACHMENT", attachment.getId(), requestId, "api",
                    java.util.Map.of("ownerType", ownerType, "ownerId", ownerId.toString(), "purpose", purpose.name(),
                            "filename", attachment.getOriginalFilename(), "mediaType", attachment.getDetectedMediaType(),
                            "sizeBytes", attachment.getSizeBytes())));
            return summary(attachment);
        } catch (AttachmentValidationException error) {
            store.discard(staged); throw new AttachmentProblem(HttpStatus.UNPROCESSABLE_ENTITY, error.code());
        }
    }

    public Attachment get(UUID id) { return repository.findByHouseholdIdAndId(current.requireCurrent().householdId(), id)
            .filter(value -> value.getStatus()!=AttachmentStatus.DELETED)
            .orElseThrow(() -> new AttachmentProblem(HttpStatus.NOT_FOUND,"ATTACHMENT_NOT_FOUND")); }
    public AttachmentSummary summary(UUID id){ return summary(get(id)); }
    public List<AttachmentSummary> list(String ownerType, UUID ownerId){ return repository
            .findByHouseholdIdAndOwnerTypeAndOwnerIdAndStatusOrderByCreatedAtDesc(current.requireCurrent().householdId(), ownerType, ownerId, AttachmentStatus.AVAILABLE)
            .stream().map(this::summary).toList(); }
    public void delete(UUID id) throws IOException { CurrentHousehold c=current.requireCurrent(); requireWriter(c); Attachment a=get(id);
        transactions.executeWithoutResult(s->{a.deleted();repository.save(a);}); store.delete(a.getStorageKey());
        events.publishEvent(new AuditEvent(UUID.randomUUID(), c.householdId(), Instant.now(), "AttachmentDeleted", "SUCCESS",
                c.accountId(), "ATTACHMENT", a.getId(), RequestContext.requireRequestId(), "api",
                java.util.Map.of("ownerType", a.getOwnerType(), "ownerId", a.getOwnerId().toString(), "purpose", a.getPurpose().name()))); }
    public InputStream content(Attachment attachment) throws IOException {
        if (!store.exists(attachment.getStorageKey())) { transactions.executeWithoutResult(s->{attachment.missing();repository.save(attachment);});
            throw new AttachmentProblem(HttpStatus.GONE,"ATTACHMENT_CONTENT_MISSING"); }
        return store.open(attachment.getStorageKey());
    }
    private void validateOwner(UUID household, String type, UUID id){
        boolean exists = switch(type){ case "ITEM_DEFINITION" -> catalog.find(household,id).map(item -> {if(item.archived()) throw new AttachmentProblem(HttpStatus.CONFLICT,"ATTACHMENT_OWNER_ARCHIVED"); return true;}).orElse(false);
            case "INVENTORY_ENTRY" -> inventory.existsEntry(household,id); default -> throw new AttachmentProblem(HttpStatus.UNPROCESSABLE_ENTITY,"ATTACHMENT_OWNER_TYPE_INVALID");};
        if(!exists) throw new AttachmentProblem(HttpStatus.NOT_FOUND,"ATTACHMENT_OWNER_NOT_FOUND");
    }
    private void requireWriter(CurrentHousehold c){if(c.role()== IdentityRole.VIEWER) throw new AttachmentProblem(HttpStatus.FORBIDDEN,"FORBIDDEN");}
    private AttachmentPurpose parsePurpose(String value){try{return AttachmentPurpose.valueOf(value);}catch(Exception e){throw new AttachmentProblem(HttpStatus.UNPROCESSABLE_ENTITY,"ATTACHMENT_PURPOSE_INVALID");}}
    private AttachmentSummary summary(Attachment a){return new AttachmentSummary(a.getId(),a.getOwnerType(),a.getOwnerId(),a.getPurpose().name(),a.getOriginalFilename(),a.getDetectedMediaType(),a.getSizeBytes(),a.getStatus().name(),a.getCreatedAt());}
}
