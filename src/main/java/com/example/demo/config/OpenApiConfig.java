package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI publicWorksOpenApi() {
        return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))).info(new Info()
                .title("Obras Publicas API")
                .description("Modulo de Gestion de Obras Publicas - Sistema Municipal")
                .version("v1"));
    }

    @Bean
    OpenApiCustomizer protectedOperations() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/public-works/")) {
                item.readOperations().forEach(operation -> operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth")));
            }
        });
    }
}
