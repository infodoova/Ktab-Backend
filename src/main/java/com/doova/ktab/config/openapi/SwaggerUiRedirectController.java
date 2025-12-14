package com.doova.ktab.config.openapi;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SwaggerUiRedirectController {

    @GetMapping({"/swagger-ui", "/swagger-ui/"})
    public String redirectToSwaggerUi() {
        return "forward:/swagger-ui/index.html";
    }
}

