package com.doova.doovafeeds.model;

import com.doova.doovafeeds.model.embeddables.Audit;
import com.doova.doovafeeds.model.listener.AudiInterface;
import com.doova.doovafeeds.model.listener.AuditListener;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@EntityListeners(AuditListener.class)
//@JsonFilter("AuditFilter")
@MappedSuperclass
@ToString(exclude = {"audit"})
public class BaseEntity implements Serializable, AudiInterface {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "col_id")
    private Long id;

    @Embedded
    @JsonIgnore
    private Audit audit;


}
