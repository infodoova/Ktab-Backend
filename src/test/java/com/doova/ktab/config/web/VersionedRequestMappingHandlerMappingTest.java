package com.doova.ktab.config.web;

import com.doova.ktab.annotation.ApiVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import static org.assertj.core.api.Assertions.assertThat;

class VersionedRequestMappingHandlerMappingTest {

    @RestController
    @ApiVersion(1)
    @RequestMapping("/plain")
    static class Versioned {
        @GetMapping("/a")
        public void a() {
        }
    }

    @RestController
    @ApiVersion(value = 1, keepLegacyPath = true)
    @RequestMapping("/ocr")
    static class VersionedWithLegacy {
        @GetMapping("/a")
        public void a() {
        }

        @PostMapping(path = "/b", consumes = "multipart/form-data")
        public void b() {
        }
    }

    @RestController
    @RequestMapping("/api/raw")
    static class Unversioned {
        @GetMapping("/a")
        public void a() {
        }
    }

    private VersionedRequestMappingHandlerMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new VersionedRequestMappingHandlerMapping();
        mapping.setApplicationContext(new StaticApplicationContext());
        mapping.afterPropertiesSet();
    }

    private RequestMappingInfo infoOf(Class<?> controller, String method) throws Exception {
        return mapping.getMappingForMethod(controller.getMethod(method), controller);
    }

    @Test
    void aVersionedControllerIsServedUnderTheVersionOnly() throws Exception {
        assertThat(infoOf(Versioned.class, "a").getPatternValues()).containsExactly("/api/v1/plain/a");
    }

    @Test
    void aVersionedControllerThatKeepsItsLegacyPathIsServedAtBoth() throws Exception {
        assertThat(infoOf(VersionedWithLegacy.class, "a").getPatternValues())
                .containsExactlyInAnyOrder("/api/v1/ocr/a", "/api/ocr/a");
    }

    @Test
    void theLegacyPathKeepsTheOtherConditionsOfTheMapping() throws Exception {
        RequestMappingInfo info = infoOf(VersionedWithLegacy.class, "b");

        assertThat(info.getPatternValues()).containsExactlyInAnyOrder("/api/v1/ocr/b", "/api/ocr/b");
        assertThat(info.getMethodsCondition().getMethods()).extracting(Enum::name).containsExactly("POST");
        assertThat(info.getConsumesCondition().getConsumableMediaTypes()).hasSize(1);
    }

    @Test
    void aControllerWithoutAVersionIsLeftAlone() throws Exception {
        assertThat(infoOf(Unversioned.class, "a").getPatternValues()).containsExactly("/api/raw/a");
    }
}
