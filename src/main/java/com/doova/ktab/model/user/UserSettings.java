package com.doova.ktab.model.user;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * Represents the configuration and preference settings for a single user.
 * This entity has a Many-to-One relationship back to User and is typically
 * managed via a One-to-One relationship from the User side.
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "tbl_user_settings", uniqueConstraints = {
        // Enforces the one-to-one constraint on the User ID column
        @UniqueConstraint(name = "uq_user_settings_user_id", columnNames = "col_user_id")})
public class UserSettings extends BaseEntity {

    // --- Association to User (The owner of these settings) ---
    /**
     * ManyToOne relationship ensuring every setting record belongs to one User.
     * This column serves as the unique key to enforce the One-to-One relationship.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_user_id", nullable = false)
    private User user;

    // --- Core Settings Fields ---

    /**
     * User's preferred display language (e.g., 'en', 'ar').
     */
    @NotBlank
    @Column(name = "col_language", length = 5, nullable = false)
    @ColumnDefault("'en'")
    private String language = "en";

    /**
     * User's preferred timezone string (e.g., 'America/New_York').
     */
    @NotBlank
    @Column(name = "col_timezone", length = 50, nullable = false)
    @ColumnDefault("'UTC'")
    private String timezone = "UTC";

    // --- Notification Preferences ---

    /**
     * Boolean flag for whether the user wants to receive email notifications.
     */
    @NotNull
    @Column(name = "col_notifications_email", nullable = false)
    @ColumnDefault("true")
    private Boolean notificationsEmail = true;

    /**
     * Boolean flag for whether the user wants to receive in-app notifications.
     */
    @NotNull
    @Column(name = "col_notifications_in_app", nullable = false)
    @ColumnDefault("true")
    private Boolean notificationsInApp = true;

    // --- Privacy Settings ---

    /**
     * Boolean flag for whether the user's profile is publicly visible.
     */
    @NotNull
    @Column(name = "col_privacy_profile_public", nullable = false)
    @ColumnDefault("false")
    private Boolean privacyProfilePublic = false;
}