package com.doova.doovafeeds.model.embeddables;

import com.doova.doovafeeds.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.JoinColumn;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Data
@Embeddable
public class Audit {

    // Make these optional and lazy to avoid required-user errors when creating new Users
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "col_created_by", foreignKey = @ForeignKey(name = "fk_user_audit_created_by"))
    @JsonIgnore  // Prevents infinite recursion
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "col_last_modified_by", foreignKey = @ForeignKey(name = "fk_user_audit_modified_by"))
    @JsonIgnore  // Prevents infinite recursion
    private User lastModifiedBy;

    // Timestamps
    @CreationTimestamp
    @Column(name = "col_created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "col_updated_at")
    private Instant updatedAt;
}
