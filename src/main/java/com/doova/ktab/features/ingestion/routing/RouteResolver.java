package com.doova.ktab.features.ingestion.routing;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.features.ingestion.config.IngestionProperties;

/** Pure routing decision, so the Studio and OCR switches are unit-testable. */
final class RouteResolver {

    private RouteResolver() {
    }

    static IngestionRoute resolve(PdfType type, IngestionProperties.Classification cfg, boolean studioEnabled,
                                  boolean ocrEnabled, IngestionRoute lockedRoute, boolean locked) {
        IngestionRoute route;
        if (locked && lockedRoute != null) {
            route = lockedRoute;
        } else if (!cfg.isEnabled() || cfg.isShadowMode()) {
            route = IngestionRoute.OCR;
        } else {
            route = cfg.getRouting().getOrDefault(type, IngestionRoute.OCR);
        }
        // The Studio switch: without Studio access, Studio's books go to Ktab's own extraction.
        if (route == IngestionRoute.STUDIO && !studioEnabled) {
            return IngestionRoute.NATIVE;
        }
        // The OCR switch: OCR is off (owner, 2026-10-01), so OCR's books go to Ktab's own extraction too.
        if (route == IngestionRoute.OCR && !ocrEnabled) {
            return IngestionRoute.NATIVE;
        }
        return route;
    }
}
