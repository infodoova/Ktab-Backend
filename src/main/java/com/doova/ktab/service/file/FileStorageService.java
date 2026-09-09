package com.doova.ktab.service.file;

import com.doova.ktab.enums.book.UrlStrategy;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    /**
     * Stores a file and returns the unique path/key where it was saved.
     *
     * @param file         The file to be stored.
     * @param directoryKey The storage directory path/prefix (e.g., "books/123/cover").
     * @return The full path/key of the stored file (e.g., "books/123/cover/image.jpg").
     * @throws IOException If the file stream cannot be read or storage fails.
     */
    String storeFile(MultipartFile file, String directoryKey) throws IOException;

    /**
     * Deletes a file from the underlying storage.
     * Implementations should be idempotent.
     *
     * @param keyName The key/name of the file in storage.
     */
    void deleteFile(String keyName);

    /**
     * Returns a public or pre-signed URL to access the file.
     *
     * @param keyName     The key/name of the file in storage.
     * @param urlStrategy The URL resolution strategy.
     * @return An URL string that can be used by the client.
     */
    String getFileUrl(String keyName, UrlStrategy urlStrategy);

    /**
     * Stores byte array content in storage.
     */
    String storeBytes(byte[] content, String contentType, String directoryKey, String extension);

    /**
     * Retrieves byte array content from storage.
     */
    byte[] getBytes(String key);
}
