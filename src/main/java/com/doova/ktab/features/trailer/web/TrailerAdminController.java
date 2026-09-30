package com.doova.ktab.features.trailer.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.trailer.oauth.HiggsfieldOAuthService;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/admin/trailer-agent", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
public class TrailerAdminController {

    private final HiggsfieldOAuthService oauth;
    private final TrailerService service;
    private final MessageSource messages;

    /** Returns the Higgsfield authorize URL; the admin opens it and approves Ktab's access. */
    @PostMapping("/higgsfield/connect")
    public ResponseEntity<ApiResponse<Map<String, String>>> connect(@CurrentUser User admin) {
        return ResponseUtils.success(Map.of("authorizeUrl", oauth.begin(admin.getId())),
                ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @PostMapping("/trailers/{id}/review")
    public ResponseEntity<ApiResponse<TrailerView>> review(@CurrentUser User admin, @PathVariable Long id,
                                                           @RequestParam boolean approve) {
        return ResponseUtils.success(service.review(admin, id, approve),
                ApiMessageKey.TRAILER_REVIEWED.getMessage(messages), HttpStatus.OK);
    }
}
