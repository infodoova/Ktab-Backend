package com.doova.ktab.features.story.model;

import com.doova.ktab.features.story.enums.SessionStatus;
import com.doova.ktab.features.story.util.SessionStateConverter;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "tbl_reading_sessions",
        indexes = {
                @Index(
                        name = "idx_reading_sessions_story_reader",
                        columnList = "col_story_id, col_reader_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSession extends BaseEntity {

    // -------------------------------
    // FK → Story
    // -------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "col_story_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_reading_session_story")
    )
    private Story story;

    // -------------------------------
    // FK → Reader (User)
    // -------------------------------

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "col_reader_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_reading_session_reader")
    )
    private User reader;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Convert(converter = SessionStateConverter.class)
    @Column(name = "col_state_json", columnDefinition = "TEXT", nullable = false)
    private SessionState state;

    @Column(name = "col_rolling_summary", columnDefinition = "TEXT")
    private String rollingSummary;

    @Column(name = "col_last_summarized_turn_index", nullable = false)
    private int lastSummarizedTurnIndex = 0;

    // -------------------------------
    // Turns (ONE → MANY)
    // -------------------------------

    @OneToMany(
            mappedBy = "session",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<Turn> turns = new HashSet<>();

    // -------------------------------
    // Constructors
    // -------------------------------

    public ReadingSession(Story story, User reader, SessionState initialState) {
        this.story = story;
        this.reader = reader;
        this.state = initialState;
    }

    // -------------------------------
    // Helpers
    // -------------------------------

    public void addTurn(Turn turn) {
        turns.add(turn);
        turn.setSession(this);
    }

    public void removeTurn(Turn turn) {
        turns.remove(turn);
        turn.setSession(null);
    }
}
