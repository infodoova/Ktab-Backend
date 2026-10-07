package com.doova.ktab.config.web;

import com.doova.ktab.annotation.ApiVersion;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

public class VersionedRequestMappingHandlerMapping
        extends RequestMappingHandlerMapping {

    private static final String API_PREFIX = "/api/v";
    private static final String LEGACY_PREFIX = "/api";

    @Override
    protected RequestMappingInfo getMappingForMethod(
            Method method,
            Class<?> handlerType
    ) {

        RequestMappingInfo mapping =
                super.getMappingForMethod(method, handlerType);

        if (mapping == null) {
            return null;
        }

        ApiVersion methodVersion =
                AnnotationUtils.findAnnotation(method, ApiVersion.class);

        ApiVersion typeVersion =
                AnnotationUtils.findAnnotation(handlerType, ApiVersion.class);

        ApiVersion apiVersion =
                methodVersion != null ? methodVersion : typeVersion;

        if (apiVersion == null) {
            return mapping;
        }

        RequestMappingInfo versioned = RequestMappingInfo
                .paths(API_PREFIX + apiVersion.value())
                .build()
                .combine(mapping);

        if (!apiVersion.keepLegacyPath()) {
            return versioned;
        }

        Set<String> patterns = new LinkedHashSet<>(versioned.getPatternValues());
        patterns.addAll(RequestMappingInfo.paths(LEGACY_PREFIX).build().combine(mapping).getPatternValues());
        return versioned.mutate().paths(patterns.toArray(String[]::new)).build();
    }
}
