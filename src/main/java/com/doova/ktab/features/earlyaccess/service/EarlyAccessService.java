package com.doova.ktab.features.earlyaccess.service;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.earlyaccess.model.EarlyAccessOrganization;
import com.doova.ktab.features.earlyaccess.model.EarlyAccessSignup;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessOrganizationRequest;
import com.doova.ktab.features.earlyaccess.repository.EarlyAccessSignupRepository;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupRequest;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EarlyAccessService {

    private final EarlyAccessSignupRepository signups;

    /** Registers a visitor. A second signup with the same address (any capitalization) is a 409; granting access is not done here. */
    @Transactional
    public EarlyAccessSignupResponse signUp(EarlyAccessSignupRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (signups.existsByEmailIgnoreCase(email)) {
            throw new EarlyAccessConflictException(ApiMessageKey.EARLY_ACCESS_ALREADY_REGISTERED);
        }

        EarlyAccessSignup signup = new EarlyAccessSignup();
        signup.setEmail(email);
        signup.setFullName(request.fullName());
        signup.setPhoneNumber(request.phoneNumber());
        signup.setGender(request.gender());
        signup.setRole(request.role());
        signup.setOrganization(organizationOf(request.organization()));
        signup.setPlan(request.plan());
        // earlyAccess stays false: the visitor asks, an admin grants

        EarlyAccessSignup saved;
        try {
            saved = signups.saveAndFlush(signup);
        } catch (DataIntegrityViolationException raced) {
            // two requests with the same address at once: the unique index let one through
            throw new EarlyAccessConflictException(ApiMessageKey.EARLY_ACCESS_ALREADY_REGISTERED);
        }
        return new EarlyAccessSignupResponse(saved.getEmail(), saved.getFullName(), saved.getRole(),
                saved.getOrganization() == null ? null : saved.getOrganization().getName(),
                saved.getPlan(), saved.isEarlyAccess());
    }

    private static EarlyAccessOrganization organizationOf(EarlyAccessOrganizationRequest request) {
        if (request == null) {
            return null;
        }
        EarlyAccessOrganization organization = new EarlyAccessOrganization();
        organization.setName(request.name());
        organization.setDescription(request.description());
        organization.setCity(request.city());
        organization.setCountry(request.country());
        organization.setAddress(request.address());
        organization.setWebsite(request.website());
        organization.setEmail(request.email());
        organization.setPhone(request.phone());
        return organization;
    }
}
