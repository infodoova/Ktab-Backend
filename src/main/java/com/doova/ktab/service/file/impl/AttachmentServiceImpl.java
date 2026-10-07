package com.doova.ktab.service.file.impl;

import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.repository.attachment.AttachmentRepository;
import com.doova.ktab.service.file.AttachmentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;

    @Override
    public Attachment save(Attachment attachment) {
        return attachmentRepository.save(attachment);
    }

    @Override
    public List<Attachment> getAllAttachmentsForEntity(Long entityId, String entityType) {
        return attachmentRepository.findAllByEntityIdAndEntityType(entityId, entityType);
    }

    @Override
    public Optional<Attachment> getAttachment(Long entityId, String entityType, String type) {
        return attachmentRepository.findByEntityIdAndEntityTypeAndType(entityId, entityType, type);
    }

    @Override
    public java.util.Map<Long, Attachment> getAttachments(java.util.Collection<Long> entityIds, String entityType, String type) {
        if (entityIds == null || entityIds.isEmpty()) {
            return java.util.Map.of();
        }
        return attachmentRepository.findAllByEntityTypeAndTypeAndEntityIdIn(entityType, type, entityIds).stream()
                .collect(java.util.stream.Collectors.toMap(Attachment::getEntityId, a -> a, (first, second) -> first));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!attachmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Attachment not found with ID: " + id);
        }
        attachmentRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteAttachmentsByEntity(Long entityId, String entityType) {
        attachmentRepository.deleteByEntityIdAndEntityType(entityId, entityType);
    }
}
