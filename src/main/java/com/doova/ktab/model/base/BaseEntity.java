package com.doova.ktab.model.base;

import com.doova.ktab.model.embeddables.Audit;
import com.doova.ktab.model.listener.interfaces.AudiInterface;
import com.doova.ktab.model.listener.AuditListener;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@EntityListeners(AuditListener.class)
@MappedSuperclass
@ToString(exclude = {"audit"})
@Getter
@Setter
public class BaseEntity implements Serializable, AudiInterface {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "col_id")
    private Long id;

    @Embedded
    @AssociationOverrides({@AssociationOverride(name = "createdBy", joinColumns = @JoinColumn(name = "col_created_by")), @AssociationOverride(name = "lastModifiedBy", joinColumns = @JoinColumn(name = "col_last_modified_by"))})
    @JsonIgnore
    private Audit audit;

    @Version
    private int version;

}
