package com.doova.ktab.service.file;

import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.repository.attachment.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException; // Adding this for robust single delete

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;

    /**
     * Saves an attachment record.
     *
     * @param attachment The attachment entity to save.
     * @return The saved attachment.
     */
    public Attachment save(Attachment attachment) {
        // This save participates in the calling BookService transaction
        return attachmentRepository.save(attachment);
    }

    /**
     * Retrieves all attachments associated with a specific entity.
     * This is used by BookService.deleteBook to find the S3 keys to delete.
     * (Assumes AttachmentRepository has List<Attachment> findAllByEntityIdAndEntityType(Long entityId, String entityType);)
     * * @param entityId The ID of the parent entity (e.g., Book ID).
     *
     * @param entityType The type of the parent entity (e.g., "Book").
     * @return A list of attachments.
     */
    public List<Attachment> getAllAttachmentsForEntity(Long entityId, String entityType) {
        // NOTE: Ensure your AttachmentRepository has this method implemented.
        return attachmentRepository.findAllByEntityIdAndEntityType(entityId, entityType);
    }

    /**
     * Retrieves a specific attachment by entity, entity type, and attachment type.
     *
     * @param entityId   The ID of the parent entity (e.g., Book ID).
     * @param entityType The type of the parent entity (e.g., "Book").
     * @param type       The type of attachment (e.g., "COVER_IMAGE").
     * @return An Optional containing the attachment.
     */
    public Optional<Attachment> getAttachment(Long entityId, String entityType, String type) {
        return attachmentRepository.findByEntityIdAndEntityTypeAndType(entityId, entityType, type);
    }

    /**
     * Deletes a single attachment record by its ID.
     * Used by BookService.deleteBook after deleting the file from S3.
     *
     * @param id The ID of the attachment record.
     */
    @Transactional
    public void delete(Long id) {
        if (!attachmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Attachment not found with ID: " + id);
        }
        attachmentRepository.deleteById(id);
    }

    /**
     * Deletes all attachments for a given entity ID and type.
     * Used for bulk deletion (e.g., if you delete the Book entity itself).
     *
     * @param entityId   The ID of the parent entity.
     * @param entityType The type of the parent entity.
     */
    @Transactional
    public void deleteAttachmentsByEntity(Long entityId, String entityType) {
        attachmentRepository.deleteByEntityIdAndEntityType(entityId, entityType);
    }
}