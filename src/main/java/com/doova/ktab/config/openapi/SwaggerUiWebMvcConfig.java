package com.doova.ktab.config.openapi;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SwaggerUiWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve swagger UI static files from META-INF/resources and webjars
        registry.addResourceHandler("/swagger-ui/**", "/webjars/**", "/swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/", "classpath:/META-INF/resources/webjars/").setCachePeriod(3600);
    }
}
