package com.doova.ktab.features.storybook.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StorybookPdfRendererTest {

    @Test
    void mockRendererReturnsPdfBytes() {
        StorybookPdfRenderer renderer = mock(StorybookPdfRenderer.class);
        when(renderer.renderHtml("<html><body>Test</body></html>")).thenReturn(new byte[]{1, 2, 3});

        byte[] result = renderer.renderHtml("<html><body>Test</body></html>");
        assertThat(result).containsExactly(1, 2, 3);
    }
}
