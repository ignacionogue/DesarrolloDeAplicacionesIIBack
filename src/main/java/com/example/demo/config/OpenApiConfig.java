package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI publicWorksOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Obras Publicas API")
                .description("Modulo de Gestion de Obras Publicas - Sistema Municipal")
                .version("v1"));
    }
}
