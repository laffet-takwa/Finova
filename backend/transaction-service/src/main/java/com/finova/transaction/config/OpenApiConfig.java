package com.finova.transaction.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI finovaOpenApi(@Value("${info.app.description}") String description) {
        return new OpenAPI()
                .info(new Info()
                        .title("Finova Transaction Service")
                        .description(description)
                        .version("1.0.0")
                        .contact(new Contact().name("Finova Engineering").email("engineering@finova.dev")))
                .externalDocs(new ExternalDocumentation()
                        .description("Finova API Gateway")
                        .url("https://api.finova.dev/docs"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}