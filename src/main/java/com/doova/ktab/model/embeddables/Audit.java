package com.doova.ktab.model.embeddables;

import com.doova.ktab.model.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Embeddable
@Data
public class Audit {

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private User lastModifiedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
