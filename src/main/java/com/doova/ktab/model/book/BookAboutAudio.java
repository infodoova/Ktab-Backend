package com.doova.ktab.model.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The short audio that introduces a book, with its description. The audio is recorded elsewhere and uploaded; the file is
 * in object storage under {@link #storagePath}. A book has at most one, so the book id is the key.
 */
@Entity
@Table(name = "tbl_book_about_audios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookAboutAudio {

    @Id
    @Column(name = "col_book_id", nullable = false, updatable = false)
    private Long bookId;

    @Column(name = "col_storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "col_file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "col_mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "col_file_size", nullable = false)
    private long fileSize;

    @Column(name = "col_duration_seconds")
    private Integer durationSeconds;

    @Column(name = "col_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "col_created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "col_updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
