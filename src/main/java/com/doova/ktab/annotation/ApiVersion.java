package com.doova.ktab.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {
    int value();

    /**
     * Also serves the same paths without the version, under {@code /api} (so {@code /ocr} answers at both
     * {@code /api/v1/ocr} and {@code /api/ocr}). For controllers that were published before they were versioned and
     * still have clients on the old URL.
     */
    boolean keepLegacyPath() default false;
}
