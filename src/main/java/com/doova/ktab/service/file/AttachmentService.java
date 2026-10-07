package com.doova.ktab.service.file;

import com.doova.ktab.model.attachment.Attachment;

import java.util.List;
import java.util.Optional;

public interface AttachmentService {

    Attachment save(Attachment attachment);

    List<Attachment> getAllAttachmentsForEntity(Long entityId, String entityType);

    Optional<Attachment> getAttachment(Long entityId, String entityType, String type);

    /** The attachment of the given type for each of the entities that has one, keyed by entity id; one query for all. */
    java.util.Map<Long, Attachment> getAttachments(java.util.Collection<Long> entityIds, String entityType, String type);

    void delete(Long id);

    void deleteAttachmentsByEntity(Long entityId, String entityType);
}