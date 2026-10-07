package com.doova.ktab.features.ocr.web;

import com.doova.ktab.features.ocr.quota.ConfigQuotaProvider;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotaControllerTest {

    private final ConfigQuotaProvider quota = mock(ConfigQuotaProvider.class);
    private final DynamicConcurrencyGate gate = mock(DynamicConcurrencyGate.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new QuotaController(quota, gate, mock(MessageSource.class))).build();
        when(quota.currentMaxParallelRequests()).thenReturn(4);
        when(gate.currentMax()).thenReturn(3);
    }

    @Test
    void theQuotaComesBackInsideTheStandardEnvelope() throws Exception {
        mvc.perform(get("/ocr/quotas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maxParallelRequests").value(4))
                .andExpect(jsonPath("$.data.gateCurrentMax").value(3));
    }

    @Test
    void settingTheLimitUpdatesTheQuotaAndRefreshesTheGate() throws Exception {
        mvc.perform(post("/ocr/quotas/max-parallel").param("value", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maxParallelRequests").value(4));

        verify(quota).setMaxParallel(6);
        verify(gate).refresh();
    }
}
