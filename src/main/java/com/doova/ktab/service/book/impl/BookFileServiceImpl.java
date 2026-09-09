package com.doova.ktab.service.book.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.S3UploadException;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.service.book.BookFileService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.validator.ImageValidator;
import com.doova.ktab.utils.validator.PdfValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    public void handleCreateFiles(Book book, MultipartFile coverImage, MultipartFile pdfFile) {

        validateCoverIfPresent(coverImage);
        validatePdfIfPresent(pdfFile);

        Long authorId = book.getAuthor().getId();
        String coverDirectory = "books/cover/" + authorId;
        String pdfDirectory = "books/pdf/" + authorId;

        String coverPath = null;
        String pdfPath = null;

        List<String> keysToDeleteAfterCommit = new ArrayList<>();
        List<String> keysToDeleteOnRollback = new ArrayList<>();

        registerS3CleanupSynchronization(keysToDeleteAfterCommit, keysToDeleteOnRollback);

        try {
            if (coverImage != null && !coverImage.isEmpty()) {
                coverPath = fileStorageService.storeFile(coverImage, coverDirectory);
                keysToDeleteOnRollback.add(coverPath);
            }

            if (pdfFile != null && !pdfFile.isEmpty()) {
                pdfPath = fileStorageService.storeFile(pdfFile, pdfDirectory);
                keysToDeleteOnRollback.add(pdfPath);
            }
        } catch (Exception e) {
            throw new S3UploadException(ApiMessageKey.FILE_UPLOAD_FAILED);
        }

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
    public void handleUpdateFiles(Book book, MultipartFile coverImage, MultipartFile pdfFile) {

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

        Long authorId = book.getAuthor().getId();
        String coverDirectory = "books/cover/" + authorId;
        String pdfDirectory = "books/pdf/" + authorId;

        try {
            if (coverImage != null && !coverImage.isEmpty()) {
                newCoverPath = fileStorageService.storeFile(coverImage, coverDirectory);
                keysToDeleteOnRollback.add(newCoverPath);
                if (oldCoverKey != null) keysToDeleteAfterCommit.add(oldCoverKey);
            }

            if (pdfFile != null && !pdfFile.isEmpty()) {
                newPdfPath = fileStorageService.storeFile(pdfFile, pdfDirectory);
                keysToDeleteOnRollback.add(newPdfPath);
                if (oldPdfKey != null) keysToDeleteAfterCommit.add(oldPdfKey);
            }
        } catch (Exception e) {
            throw new S3UploadException(ApiMessageKey.FILE_UPLOAD_FAILED);
        }

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

        List<String> keysToDeleteAfterCommit = attachments.stream().map(Attachment::getStoragePath).filter(Objects::nonNull).filter(path -> !path.isBlank()).toList();

        registerS3CleanupSynchronization(keysToDeleteAfterCommit, List.of());

        for (Attachment attachment : attachments) {
            attachmentService.delete(attachment.getId());
        }
    }

    // -------------------------------------------------------------------------
    // ATTACHMENT HELPERS
    // -------------------------------------------------------------------------
    private void createAttachment(Book book, String originalFileName, String storagePath, String type, String mimeType, Long fileSize) {
        attachmentService.save(Attachment.builder().fileName(originalFileName).storagePath(storagePath).entityId(book.getId()).entityType(BOOK_ENTITY_TYPE).type(type).user(book.getAuthor()).mimeType(mimeType).fileSize(fileSize).sourceUrl(null).build());
    }

    private void upsertAttachment(Book book, Optional<Attachment> existing, String originalFileName, String storagePath, String type, String mimeType, Long fileSize) {
        Attachment attachment = existing.orElseGet(() -> Attachment.builder().entityId(book.getId()).entityType(BOOK_ENTITY_TYPE).type(type).user(book.getAuthor()).build());

        attachment.setFileName(originalFileName);
        attachment.setStoragePath(storagePath);
        attachment.setMimeType(mimeType);
        attachment.setFileSize(fileSize);

        attachmentService.save(attachment);
    }

    // -------------------------------------------------------------------------
    // TX-AWARE S3 CLEANUP
    // -------------------------------------------------------------------------
    private void registerS3CleanupSynchronization(List<String> keysToDeleteAfterCommit, List<String> keysToDeleteOnRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                keysToDeleteAfterCommit.forEach(fileStorageService::deleteFile);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    keysToDeleteOnRollback.forEach(fileStorageService::deleteFile);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // VALIDATION
    // -------------------------------------------------------------------------
    private void validateCoverIfPresent(MultipartFile file) {
        if (file == null || file.isEmpty()) return;

        try {
            imageValidator.validateCover(file);
        } catch (Exception e) {
            throw new BadRequestException(ApiMessageKey.FILE_INVALID_IMAGE);
        }
    }

    private void validatePdfIfPresent(MultipartFile file) {
        if (file == null || file.isEmpty()) return;

        try {
            pdfValidator.validatePdf(file);
        } catch (Exception e) {
            throw new BadRequestException(ApiMessageKey.FILE_INVALID_PDF);
        }
    }
}
