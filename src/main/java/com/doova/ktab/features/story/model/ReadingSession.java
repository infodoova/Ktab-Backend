package com.doova.ktab.features.story.model;

import com.doova.ktab.features.story.enums.SessionStatus;
import com.doova.ktab.features.story.util.SessionStateConverter;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "tbl_reading_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reading_sessions_story_reader",
                        columnNames = {"col_story_id", "col_reader_id"}
                )
        },
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

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "col_story_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_reading_session_story")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Story story;

    // -------------------------------
    // FK → Reader (User)
    // -------------------------------

    @com.fasterxml.jackson.annotation.JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "col_reader_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_reading_session_reader")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
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

    @Column(name = "col_current_risk_mapping")
    private String currentRiskMapping;

    @Column(name = "col_open_threads", columnDefinition = "TEXT")
    private String openThreads;

    @Column(name = "col_unpaid_setups", columnDefinition = "TEXT")
    private String unpaidSetups;

    @Column(name = "col_inventory", columnDefinition = "TEXT")
    private String inventory;

    @Column(name = "col_character_status", columnDefinition = "TEXT")
    private String characterStatus;

    // -------------------------------
    // Turns (ONE → MANY)
    // -------------------------------

    @com.fasterxml.jackson.annotation.JsonIgnore
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
