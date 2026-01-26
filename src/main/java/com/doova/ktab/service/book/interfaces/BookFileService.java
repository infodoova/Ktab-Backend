package com.doova.ktab.service.book.interfaces;

import com.doova.ktab.model.book.Book;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface BookFileService {

    /**
     * For newly created books: upload files (if any), create attachments,
     * and hook into the current transaction for S3 cleanup on rollback.
     */
    void handleCreateFiles(Book book, MultipartFile coverImage, MultipartFile pdfFile);

    /**
     * For existing books: replace cover/pdf if new files are provided.
     * Old files are deleted only after commit; new files are deleted on rollback.
     */
    void handleUpdateFiles(Book book, MultipartFile coverImage, MultipartFile pdfFile);

    /**
     * For deletion: delete attachment rows and S3 files (after commit).
     */
    void handleDeleteFiles(Book book);
}
