package com.doova.ktab.model.attachment;

import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Represents a file attachment that uses a HYBRID linking strategy:
 * 1. Specific Many-to-One relationship to User (for strong integrity).
 * 2. Generic Association (Entity/ID) for linking to any other entity (Post, Product, etc.).
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "tbl_attachments")
@Builder
public class Attachment extends BaseEntity {

    // --- Core Attachment Details ---

    @NotBlank
    @Column(name = "col_file_name", nullable = false, length = 255)
    private String fileName;

    @NotBlank
    @Column(name = "col_storage_path", nullable = false, length = 512)
    private String storagePath; // Path where the file is physically stored (local or cloud storage bucket)

    // --- Specific Association to User (Many-to-One) ---
    /**
     * Establishes a ManyToOne relationship: an attachment *can* belong to a specific User.
     * The column is nullable (nullable = true) because the attachment might belong
     * to another entity (e.g., Post), in which case the generic fields below are used.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_user_id", nullable = true)
    private User user;
    // --- End Specific Association ---


    // --- Generic Association Fields (For maximum reusability across entities) ---

    /**
     * The unique identifier (Primary Key) of the entity that owns this attachment (e.g., User ID, Post ID).
     * This field is used when the 'user' field above is null (i.e., when attached to a Post or Product).
     */
    @NotNull
    @Column(name = "col_entity_id", nullable = false)
    private Long entityId;

    /**
     * The type or class name of the entity that owns this attachment (e.g., "User", "Post", "Product").
     * This is used by the application service layer to find the owner.
     */
    @NotBlank
    @Column(name = "col_entity_type", nullable = false, length = 50)
    private String entityType;
    // --- End Generic Association ---


    // --- Specific Attachment Metadata ---

    /**
     * Categorizes the purpose of the attachment (eg., "PROFILE_PICTURE", "IMAGE_COVER", "LEGAL_DOCUMENT").
     */
    @Column(name = "col_attachment_type", length = 50)
    private String type;

    /**
     * External URL source for the file, useful if the file is hosted externally (e.g., a CDN or third-party site).
     */
    @Column(name = "col_source_url", length = 512)
    private String sourceUrl;

    @Column(name = "col_mime_type", length = 100)
    private String mimeType;

    @Column(name = "col_file_size")
    private Long fileSize; // File size in bytes
}