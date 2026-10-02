package com.doova.ktab.features.trailer.web;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.oauth.HiggsfieldOAuthService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerPublicControllerTest {

    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerService service = mock(TrailerService.class);
    final HiggsfieldOAuthService oauth = mock(HiggsfieldOAuthService.class);
    final TrailerPublicController controller = new TrailerPublicController(gateway, service, oauth);

    @Test
    void invalidSignatureIsRejected() {
        when(gateway.verifyWebhook(anyString(), anyMap())).thenReturn(Optional.empty());
        assertThat(controller.webhook("{}", Map.of()).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test
    void idledSessionPullsTheCheckForward() {
        when(gateway.verifyWebhook(anyString(), anyMap())).thenReturn(Optional.of(
                new TrailerAgentGateway.WebhookNotice("whe_1", "session.status_idled", "sesn_1")));

        assertThat(controller.webhook("{}", Map.of()).getStatusCode().value()).isEqualTo(204);
        verify(service).nudge("sesn_1");
    }

    @Test
    void duplicateWebhookIsIgnored() {
        when(gateway.verifyWebhook(anyString(), anyMap())).thenReturn(Optional.of(
                new TrailerAgentGateway.WebhookNotice("whe_2", "session.status_idled", "sesn_1")));

        controller.webhook("{}", Map.of());
        controller.webhook("{}", Map.of());

        verify(service, times(1)).nudge("sesn_1");
    }
}
