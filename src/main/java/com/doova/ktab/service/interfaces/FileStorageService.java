package com.doova.ktab.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface FileStorageService {

    /**
     * Saves the file to physical storage (local disk, S3, etc.) and returns the storage path.
     * @param file The file content.
     * @param subdirectory A context for where to save the file (e.g., "book-123/cover").
     * @return The final, resolvable path to the stored file.
     * @throws IOException if the file saving fails (disk full, permission denied, etc.).
     */
    String storeFile(MultipartFile file, String subdirectory) throws IOException;
}
