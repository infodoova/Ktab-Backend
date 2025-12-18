package com.doova.ktab.api.version;

import com.doova.ktab.annotation.ApiVersion;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

public class VersionedRequestMappingHandlerMapping
        extends RequestMappingHandlerMapping {

    private static final String API_PREFIX = "/api/v";

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

        String versionPrefix = API_PREFIX + apiVersion.value();

        return RequestMappingInfo
                .paths(versionPrefix)
                .build()
                .combine(mapping);
    }
}

