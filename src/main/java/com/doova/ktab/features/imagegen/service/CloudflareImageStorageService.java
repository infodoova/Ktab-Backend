package com.doova.ktab.features.imagegen.service;

import java.util.UUID;

/**
 * Service for managing AI-generated image artifacts in Cloudflare R2.
 */
public interface CloudflareImageStorageService {

    /**
     * Uploads generated image bytes to Cloudflare R2 with immutable edge-cache directives.
     *
     * @param bookId     The target book identifier
     * @param userId     The requesting user identifier
     * @param imageId    The unique image identifier
     * @param imageBytes The raw image binary payload
     * @param mimeType   The MIME type (e.g. "image/png")
     * @return The storage key in Cloudflare R2
     */
    String uploadImage(Long bookId, Long userId, UUID imageId, byte[] imageBytes, String mimeType);

    /**
     * Resolves a downloadable/displayable URL for the image (Cloudflare CDN or presigned GET URL).
     *
     * @param storageKey The storage key in Cloudflare R2
     * @return The resolved URL string
     */
    String resolveImageUrl(String storageKey);

    /**
     * Deletes the image object from Cloudflare R2.
     *
     * @param storageKey The storage key in Cloudflare R2
     */
    void deleteImage(String storageKey);
}
