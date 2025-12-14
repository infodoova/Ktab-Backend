package com.doova.ktab.service.book;

import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.service.book.interfaces.BookFileService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.interfaces.file.FileStorageService;
import com.doova.ktab.utils.validator.ImageValidator;
import com.doova.ktab.utils.validator.PdfValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of BookFileService that:
 * - Validates files (cover / pdf)
 * - Stores them using FileStorageService (e.g. S3)
 * - Manages Attachment entities
 * - Coordinates S3 cleanup with DB transaction commit/rollback
 */
@Service
@RequiredArgsConstructor
public class BookFileServiceImpl implements BookFileService {

    private final FileStorageService fileStorageService;
    private final AttachmentService attachmentService;
    private final ImageValidator imageValidator;
    private final PdfValidator pdfValidator;

    private static final String BOOK_ENTITY_TYPE = Book.class.getSimpleName();

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Override
    public void handleCreateFiles(Book book, MultipartFile coverImage, MultipartFile pdfFile) throws IOException {

        // 0) Validate
        validateCoverIfPresent(coverImage);
        validatePdfIfPresent(pdfFile);

        Long authorId = book.getAuthor().getId();

        String coverDirectory = "books/cover/" + authorId;
        String pdfDirectory = "books/pdf/" + authorId;

        String coverPath = null;
        String pdfPath = null;

        List<String> keysToDeleteAfterCommit = new ArrayList<>(); // none on create
        List<String> keysToDeleteOnRollback = new ArrayList<>();

        registerS3CleanupSynchronization(keysToDeleteAfterCommit, keysToDeleteOnRollback);

        if (coverImage != null && !coverImage.isEmpty()) {
            coverPath = fileStorageService.storeFile(coverImage, coverDirectory);
            keysToDeleteOnRollback.add(coverPath);
        }

        if (pdfFile != null && !pdfFile.isEmpty()) {
            pdfPath = fileStorageService.storeFile(pdfFile, pdfDirectory);
            keysToDeleteOnRollback.add(pdfPath);
        }

        // Create attachments
        if (coverPath != null) {
            createAttachment(book, coverImage.getOriginalFilename(), coverPath, "COVER_IMAGE", coverImage.getContentType(), coverImage.getSize());
        }

        if (pdfPath != null) {
            createAttachment(book, pdfFile.getOriginalFilename(), pdfPath, "PDF_SOURCE", pdfFile.getContentType(), pdfFile.getSize());
        }
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------
    @Override
    public void handleUpdateFiles(Book book, MultipartFile coverImage, MultipartFile pdfFile) throws IOException {

        // 0) Validate
        validateCoverIfPresent(coverImage);
        validatePdfIfPresent(pdfFile);

        Optional<Attachment> existingCover = attachmentService.getAttachment(book.getId(), BOOK_ENTITY_TYPE, "COVER_IMAGE");
        Optional<Attachment> existingPdf = attachmentService.getAttachment(book.getId(), BOOK_ENTITY_TYPE, "PDF_SOURCE");

        String oldCoverKey = existingCover.map(Attachment::getStoragePath).orElse(null);
        String oldPdfKey = existingPdf.map(Attachment::getStoragePath).orElse(null);

        String newCoverPath = null;
        String newPdfPath = null;

        List<String> keysToDeleteAfterCommit = new ArrayList<>();
        List<String> keysToDeleteOnRollback = new ArrayList<>();

        registerS3CleanupSynchronization(keysToDeleteAfterCommit, keysToDeleteOnRollback);

        // 🔥 Use SAME author-based directories as create
        Long authorId = book.getAuthor().getId();
        String coverDirectory = "books/cover/" + authorId;
        String pdfDirectory = "books/pdf/" + authorId;

        if (coverImage != null && !coverImage.isEmpty()) {
            newCoverPath = fileStorageService.storeFile(coverImage, coverDirectory);
            keysToDeleteOnRollback.add(newCoverPath);
            if (oldCoverKey != null) {
                keysToDeleteAfterCommit.add(oldCoverKey);
            }
        }

        if (pdfFile != null && !pdfFile.isEmpty()) {
            newPdfPath = fileStorageService.storeFile(pdfFile, pdfDirectory);
            keysToDeleteOnRollback.add(newPdfPath);
            if (oldPdfKey != null) {
                keysToDeleteAfterCommit.add(oldPdfKey);
            }
        }

        // Upsert attachments
        if (newCoverPath != null) {
            upsertAttachment(book, existingCover, coverImage.getOriginalFilename(), newCoverPath, "COVER_IMAGE", coverImage.getContentType(), coverImage.getSize());
        }

        if (newPdfPath != null) {
            upsertAttachment(book, existingPdf, pdfFile.getOriginalFilename(), newPdfPath, "PDF_SOURCE", pdfFile.getContentType(), pdfFile.getSize());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Override
    public void handleDeleteFiles(Book book) {

        List<Attachment> attachments = attachmentService.getAllAttachmentsForEntity(book.getId(), BOOK_ENTITY_TYPE);

        List<String> keysToDeleteAfterCommit = attachments.stream().map(Attachment::getStoragePath).filter(Objects::nonNull).filter(path -> !path.isBlank()).collect(Collectors.toList());

        List<String> keysToDeleteOnRollback = List.of();

        registerS3CleanupSynchronization(keysToDeleteAfterCommit, keysToDeleteOnRollback);

        // Delete attachment records now (inside TX)
        for (Attachment attachment : attachments) {
            attachmentService.delete(attachment.getId());
        }
    }

    // -------------------------------------------------------------------------
    // ATTACHMENT HELPERS
    // -------------------------------------------------------------------------
    private void createAttachment(Book book, String originalFileName, String storagePath, String type, String mimeType, Long fileSize) {
        Attachment attachment = Attachment.builder().fileName(originalFileName).storagePath(storagePath).entityId(book.getId()).entityType(BOOK_ENTITY_TYPE).type(type).user(book.getAuthor()).mimeType(mimeType).fileSize(fileSize).sourceUrl(null).build();

        attachmentService.save(attachment);
    }

    private void upsertAttachment(Book book, Optional<Attachment> existingAttachment, String originalFileName, String storagePath, String type, String mimeType, Long fileSize) {
        Attachment attachment;
        if (existingAttachment.isPresent()) {
            attachment = existingAttachment.get();
            attachment.setFileName(originalFileName);
            attachment.setStoragePath(storagePath);
            attachment.setMimeType(mimeType);
            attachment.setFileSize(fileSize);
        } else {
            attachment = Attachment.builder().fileName(originalFileName).storagePath(storagePath).entityId(book.getId()).entityType(BOOK_ENTITY_TYPE).type(type).user(book.getAuthor()).mimeType(mimeType).fileSize(fileSize).sourceUrl(null).build();
        }
        attachmentService.save(attachment);
    }

    // -------------------------------------------------------------------------
    // TX-AWARE S3 CLEANUP
    // -------------------------------------------------------------------------
    private void registerS3CleanupSynchronization(List<String> keysToDeleteAfterCommit, List<String> keysToDeleteOnRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                for (String key : keysToDeleteAfterCommit) {
                    try {
                        fileStorageService.deleteFile(key);
                    } catch (Exception e) {
                        // log & monitor (optional)
                    }
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    for (String key : keysToDeleteOnRollback) {
                        try {
                            fileStorageService.deleteFile(key);
                        } catch (Exception e) {
                            // log & monitor (optional)
                        }
                    }
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // VALIDATION HELPERS
    // -------------------------------------------------------------------------
    private void validateCoverIfPresent(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return;

        String contentType = file.getContentType();
        boolean isImage = contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/jpg"));

        if (isImage) {
            imageValidator.validateCover(file);
        }
    }

    private void validatePdfIfPresent(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return;

        String contentType = file.getContentType();
        boolean isPdf = contentType != null && (contentType.equals("application/pdf") || contentType.equals("application/x-pdf") || contentType.equals("application/acrobat") || contentType.equals("applications/vnd.pdf") || contentType.equals("text/pdf"));

        if (isPdf) {
            pdfValidator.validatePdf(file);
        }
    }
}
