package com.doova.ktab.repository.attachment;

import com.doova.ktab.model.attachment.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    // Used by BookService.getAllBooks and getBookById to construct URLs
    Optional<Attachment> findByEntityIdAndEntityTypeAndType(Long entityId, String entityType, String type);

    // Used by BookService.deleteBook to retrieve all files/keys associated with a Book
    List<Attachment> findAllByEntityIdAndEntityType(Long entityId, String entityType);

    // Used by AttachmentService.deleteAttachmentsByEntity
    void deleteByEntityIdAndEntityType(Long entityId, String entityType);

    // deleteById and existsById are inherited from JpaRepository
}