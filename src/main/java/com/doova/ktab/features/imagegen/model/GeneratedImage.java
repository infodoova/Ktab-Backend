package com.doova.ktab.features.imagegen.model;

import com.doova.ktab.features.imagegen.enums.ImageAspectRatio;
import com.doova.ktab.features.imagegen.enums.ImageGenerationStatus;
import com.doova.ktab.features.imagegen.enums.ImageTheme;
import com.doova.ktab.model.base.BaseUuidEntity;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Persistent record of an AI-generated book illustration stored in Cloudflare R2.
 */
@Entity
@Table(
        name = "tbl_generated_images",
        indexes = {
                @Index(name = "idx_generated_images_book_id", columnList = "col_book_id"),
                @Index(name = "idx_generated_images_user_id", columnList = "col_user_id"),
                @Index(name = "idx_generated_images_user_book", columnList = "col_book_id, col_user_id, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class GeneratedImage extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_id", nullable = false, foreignKey = @ForeignKey(name = "fk_generated_image_book"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_generated_image_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "col_user_context", nullable = false, columnDefinition = "TEXT")
    private String userContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_theme", nullable = false, length = 50)
    private ImageTheme theme;

    @Column(name = "col_aspect_ratio", nullable = false, length = 20)
    private String aspectRatio;

    @Column(name = "col_style_notes", length = 500)
    private String styleNotes;

    @Column(name = "col_prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "col_ai_model", nullable = false, length = 100)
    private String aiModel = "gemini-3.1-flash-image";

    @Column(name = "col_storage_key", length = 512)
    private String storageKey;

    @Column(name = "col_cf_public_url", length = 1024)
    private String cfPublicUrl;

    @Column(name = "col_mime_type", length = 50)
    private String mimeType = "image/png";

    @Column(name = "col_file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private ImageGenerationStatus status = ImageGenerationStatus.QUEUED;

    @Column(name = "col_failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "col_retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "col_completed_at")
    private Instant completedAt;
}
