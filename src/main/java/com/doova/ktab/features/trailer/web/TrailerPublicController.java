package com.doova.ktab.features.trailer.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.oauth.HiggsfieldOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Called by Anthropic (webhook) and by the admin's browser (OAuth callback); lives under the public /api/v1/public/**. */
@ApiVersion(1)
@RestController
@RequestMapping(path = "/public/trailer-agent")
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
@Slf4j
public class TrailerPublicController {

    private final TrailerAgentGateway gateway;
    private final TrailerService service;
    private final HiggsfieldOAuthService oauth;
    /** Dedupe on the per-event id (retries reuse it). Bounded; the reconciler covers anything forgotten. */
    private final Set<String> seen = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 10_000;
        }
    });

    public TrailerPublicController(TrailerAgentGateway gateway, TrailerService service, HiggsfieldOAuthService oauth) {
        this.gateway = gateway;
        this.service = service;
        this.oauth = oauth;
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> webhook(@RequestBody String rawBody, @RequestHeader Map<String, String> headers) {
        var notice = gateway.verifyWebhook(rawBody, headers); // raw body: re-serialized JSON breaks the HMAC
        if (notice.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        synchronized (seen) {
            if (!seen.add(notice.get().eventId())) {
                return ResponseEntity.noContent().build();
            }
        }
        switch (notice.get().type()) {
            case "session.status_idled", "session.status_terminated", "session.outcome_evaluation_ended" ->
                    service.nudge(notice.get().resourceId());
            case "vault_credential.refresh_failed" ->
                    log.error("Higgsfield vault credential {} failed to refresh — an admin must reconnect Higgsfield",
                            notice.get().resourceId());
            default -> { }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/higgsfield/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state) {
        oauth.complete(code, state);
        return ResponseEntity.ok("<html><body dir=\"rtl\"><h3>تم ربط حساب Higgsfield بنجاح. يمكنك إغلاق هذه النافذة.</h3></body></html>");
    }
}
