package com.doova.doovafeeds.model.listener;

import com.doova.doovafeeds.annotation.CurrentUser;
import com.doova.doovafeeds.model.User;
import com.doova.doovafeeds.model.embeddables.Audit;
import com.doova.doovafeeds.security.UserPrincipal;
import com.doova.doovafeeds.utils.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.*;

public class AuditListener {

    @PrePersist
    public void beforeCreate(AudiInterface audiInterface){

        Audit audit = audiInterface.getAudit();
        User currentUser = getCurrentLoggedInUser();

        if (audit == null) {
            audit = new Audit();
            audiInterface.setAudit(audit);
        }

        audit.setCreatedBy(currentUser);
//        objectMapper.setFilterProvider(getFilter());
    }

    @PreUpdate
    public void beforeUpdate(AudiInterface audiInterface) {

        Audit audit = audiInterface.getAudit();
        User currentUser = getCurrentLoggedInUser();

        if(audit==null){
            audit = new Audit();
            audiInterface.setAudit(audit);
        }

        audit.setLastModifiedBy(currentUser);

//        objectMapper.setFilterProvider(getFilter());
    }

//    @PostPersist
//    public void afterCreate(AudiInterface audiInterface) {
//        objectMapper.setFilterProvider(getFilter());
//    }
//
//    @PostUpdate
//    public void afterUpdate(AudiInterface audiInterface) {
//        objectMapper.setFilterProvider(getFilter());
//    }
//
//    @PostLoad
//    public void afterLoad(AudiInterface audiInterface) {
//        objectMapper.setFilterProvider(getFilter());
//    }

    private FilterProvider getFilter() {
        List<String> fieldsToFilter = new ArrayList<>(Arrays.asList("audit"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserPrincipal) {
                User user = ((UserPrincipal) principal).getUser();
//                if (user.getRole().equals(UserRole.ADMIN)) {
                fieldsToFilter.remove("audit");
//                }
            }
        }

        SimpleBeanPropertyFilter theFilter = SimpleBeanPropertyFilter.serializeAllExcept(new HashSet<>(fieldsToFilter));
        return new SimpleFilterProvider().addFilter("AuditFilter", theFilter);
    }

    private String getCurrentLoggedInUserName() {
        Optional<User> optionalUser = Utils.getCurrentLoggedInUser();
        return optionalUser.map(User::getEmail).orElse(null);
    }

    private User getCurrentLoggedInUser() {
        Optional<User> optionalUser = Utils.getCurrentLoggedInUser();
        return optionalUser.orElse(null);  // Return the User object directly
    }




}