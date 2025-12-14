package com.doova.ktab.model.listener;

import com.doova.ktab.model.embeddables.Audit;
import com.doova.ktab.model.listener.interfaces.AudiInterface;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.utils.Utils;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

public class AuditListener {

    @PrePersist
    public void beforeCreate(AudiInterface audiInterface) {

        Audit audit = audiInterface.getAudit();
        User currentUser = getCurrentLoggedInUser();

        if (audit == null) {
            audit = new Audit();
            audiInterface.setAudit(audit);
        }

        audit.setCreatedBy(currentUser);
    }

    @PreUpdate
    public void beforeUpdate(AudiInterface audiInterface) {

        Audit audit = audiInterface.getAudit();
        User currentUser = getCurrentLoggedInUser();

        if (audit == null) {
            audit = new Audit();
            audiInterface.setAudit(audit);
        }

        audit.setLastModifiedBy(currentUser);

    }

    private User getCurrentLoggedInUser() {
        Optional<User> optionalUser = Utils.getCurrentLoggedInUser();
        return optionalUser.orElse(null);  // Return the User object directly
    }


}