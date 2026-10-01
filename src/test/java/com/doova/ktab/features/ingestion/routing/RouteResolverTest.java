package com.doova.ktab.features.ingestion.routing;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.features.ingestion.config.IngestionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteResolverTest {

    private final IngestionProperties.Classification cfg = new IngestionProperties().getClassification();

    @Test
    void digitalGoesToStudioWhenStudioIsEnabled() {
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, true, false, null, false)).isEqualTo(IngestionRoute.STUDIO);
    }

    @Test
    void digitalGoesToNativeWhenStudioIsDisabled() {
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, false, null, false)).isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void withOcrOffEveryNonStudioBookGoesNative() {
        for (PdfType t : List.of(PdfType.SCANNED, PdfType.HYBRID_OCR, PdfType.MIXED, PdfType.UNKNOWN)) {
            assertThat(RouteResolver.resolve(t, cfg, false, false, null, false)).as(t.name()).isEqualTo(IngestionRoute.NATIVE);
            assertThat(RouteResolver.resolve(t, cfg, true, false, null, false)).as(t.name()).isEqualTo(IngestionRoute.NATIVE);
        }
    }

    @Test
    void withOcrOnScannedGoesToOcrAsToday() {
        assertThat(RouteResolver.resolve(PdfType.SCANNED, cfg, false, true, null, false)).isEqualTo(IngestionRoute.OCR);
    }

    @Test
    void aBookLockedToStudioRunsNativeWhileStudioIsDisabled() {
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, false, IngestionRoute.STUDIO, true))
                .isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void aBookLockedToOcrRunsNativeWhileOcrIsOff() {
        assertThat(RouteResolver.resolve(PdfType.SCANNED, cfg, false, false, IngestionRoute.OCR, true))
                .isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void shadowModeMeansOcrOnlyWhileOcrIsOn() {
        cfg.setShadowMode(true);
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, true, null, false)).isEqualTo(IngestionRoute.OCR);
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, false, null, false)).isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void disabledClassificationMeansOcrOnlyWhileOcrIsOn() {
        cfg.setEnabled(false);
        assertThat(RouteResolver.resolve(PdfType.UNKNOWN, cfg, true, true, null, false)).isEqualTo(IngestionRoute.OCR);
        assertThat(RouteResolver.resolve(PdfType.UNKNOWN, cfg, true, false, null, false)).isEqualTo(IngestionRoute.NATIVE);
    }
}
