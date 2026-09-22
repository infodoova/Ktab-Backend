package com.doova.ktab.model.book;

import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "tbl_book_sections",
        indexes = {
                @Index(name = "idx_book_sections_book", columnList = "col_book_id, col_sort_order"),
                @Index(name = "idx_book_sections_parent", columnList = "col_parent_id")
        }
)
@Getter
@Setter
public class BookSection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_parent_id")
    private BookSection parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<BookSection> children = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "col_section_type", nullable = false, length = 30)
    private SectionType sectionType;

    @Column(name = "col_level", nullable = false)
    private short level;

    public void setLevel(int level) {
        this.level = (short) level;
    }

    @Column(name = "col_sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "col_division_label", length = 50)
    private String divisionLabel;

    @Column(name = "col_ordinal")
    private Integer ordinal;

    @Column(name = "col_title", nullable = false, length = 1000)
    private String title;

    @Column(name = "col_title_normalized", nullable = false, length = 1000)
    private String titleNormalized;

    @Column(name = "col_printed_start_label", length = 20)
    private String printedStartLabel;

    @Column(name = "col_start_page")
    private Integer startPage;

    @Column(name = "col_end_page")
    private Integer endPage;

    @Column(name = "col_start_anchor", length = 1000)
    private String startAnchor;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_source", nullable = false, length = 20)
    private StructureSource source;

    @Column(name = "col_confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Column(name = "col_needs_review", nullable = false)
    private boolean needsReview = false;

    public void addChild(BookSection child) {
        if (child != null) {
            child.setParent(this);
            child.setBook(this.book);
            children.add(child);
        }
    }

    public void removeChild(BookSection child) {
        if (child != null && children.remove(child)) {
            child.setParent(null);
        }
    }
}
