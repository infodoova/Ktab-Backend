package com.doova.ktab.interactivestorytelling.model;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "tbl_turns",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_turns_session_turn_index",
                        columnNames = {"col_session_id", "col_turn_index"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_turns_session_turn_index",
                        columnList = "col_session_id, col_turn_index"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Turn extends BaseEntity {

    // -------------------------------
    // FK → ReadingSession
    // -------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "col_session_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_turn_session")
    )
    private ReadingSession session;

    @Column(name = "col_turn_index", nullable = false)
    private int turnIndex;

    @Column(name = "col_scene_text", columnDefinition = "TEXT", nullable = false)
    private String sceneText;

    @Column(name = "col_choices_json", columnDefinition = "TEXT", nullable = false)
    private String choicesJson;

    @Column(name = "col_chosen_choice_id")
    private String chosenChoiceId;

    @Column(name = "col_is_summarized", nullable = false)
    private boolean summarized = false;

    // -------------------------------
    // Constructor helper
    // -------------------------------

    public Turn(ReadingSession session, int turnIndex, String sceneText, String choicesJson) {
        this.session = session;
        this.turnIndex = turnIndex;
        this.sceneText = sceneText;
        this.choicesJson = choicesJson;
    }
}
