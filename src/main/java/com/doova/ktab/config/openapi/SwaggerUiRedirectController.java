package com.doova.ktab.config.openapi;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Controller
public class SwaggerUiRedirectController {

    @GetMapping({"/swagger-ui", "/swagger-ui/"})
    public String redirectToSwaggerUi() {
        return "forward:/swagger-ui/index.html";
    }
}

