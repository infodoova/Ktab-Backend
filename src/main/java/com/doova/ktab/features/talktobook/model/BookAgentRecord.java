package com.doova.ktab.features.talktobook.model;

import com.doova.ktab.features.talktobook.dto.response.BookCitation;
import com.doova.ktab.model.base.BaseUuidEntity;
import com.doova.ktab.model.book.Book;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an answered question-answer pair for a specific book.
 * Acts as the persistent semantic and exact-match cache for the Talk-to-Book feature.
 */
@Entity
@Table(
        name = "tbl_book_agent_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_book_agent_records_book_hash", columnNames = {"col_book_id", "col_question_hash"})
        },
        indexes = {
                @Index(name = "idx_book_agent_records_eviction_covering", columnList = "col_book_id, col_count_used, col_last_accessed_at"),
                @Index(name = "idx_book_agent_records_book_id", columnList = "col_book_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookAgentRecord extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_id", nullable = false, foreignKey = @ForeignKey(name = "fk_book_agent_records_book"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Book book;

    @Column(name = "col_question", nullable = false, length = 500)
    private String question;

    @Column(name = "col_question_hash", nullable = false, length = 64)
    private String questionHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_question_embedding", columnDefinition = "JSONB")
    private List<Float> questionEmbedding;

    @Column(name = "col_answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_citations", columnDefinition = "JSONB")
    private List<BookCitation> citations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_cited_pages", columnDefinition = "JSONB")
    private List<Integer> citedPages = new ArrayList<>();

    @Column(name = "col_count_used", nullable = false)
    private int countUsed = 1;

    @Column(name = "col_is_web_augmented", nullable = false)
    private boolean isWebAugmented = false;

    @Column(name = "col_last_accessed_at", nullable = false)
    private Instant lastAccessedAt = Instant.now();

    // Null on legacy rows: these must be regenerated before reuse.
    @Column(name = "col_cache_revision", length = 64)
    private String cacheRevision;

    @Column(name = "col_answer_generated_at")
    private Instant answerGeneratedAt;

    public void incrementCountUsed() {
        this.countUsed++;
        this.lastAccessedAt = Instant.now();
    }
}
