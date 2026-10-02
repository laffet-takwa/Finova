package com.finova.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The OpenAPI document every Finova service publishes.
 * <p>
 * The bearer requirement is declared globally so authenticated endpoints render
 * with the Authorize button wired; {@code AuthController} opts out per class with
 * an empty {@code @SecurityRequirements}, because a caller who cannot sign in yet
 * has no token to send.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finovaOpenApi(@Value("${info.app.description}") String description) {
        Components components = new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSchemas("ApiError", errorSchema());
        errorResponses().forEach(components::addResponses);

        return new OpenAPI()
                .info(new Info()
                        .title("Finova User Service")
                        .description(description)
                        .version("1.0.0")
                        .contact(new Contact().name("Finova Engineering").email("engineering@finova.dev")))
                .externalDocs(new ExternalDocumentation()
                        .description("Finova platform API, served by the api-gateway on port 8080")
                        .url("http://localhost:8080/v3/api-docs"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(components);
    }

    private ObjectSchema errorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperties("timestamp", new StringSchema());
        schema.addProperties("status", new StringSchema());
        schema.addProperties("code", new StringSchema());
        schema.addProperties("message", new StringSchema());
        schema.addProperties("path", new StringSchema());
        schema.addProperties("correlationId", new StringSchema());
        return schema;
    }

    /** Named error envelopes so endpoints reference them instead of repeating the shape. */
    private Map<String, ApiResponse> errorResponses() {
        Content content = new Content().addMediaType("application/json",
                new io.swagger.v3.oas.models.media.MediaType().schema(errorSchema()));

        ApiResponses responses = new ApiResponses()
                .addApiResponse("Unauthorized", new ApiResponse()
                        .description("Authentication is required")
                        .content(content))
                .addApiResponse("Forbidden", new ApiResponse()
                        .description("An ADMIN token is required")
                        .content(content))
                .addApiResponse("NotFound", new ApiResponse()
                        .description("The resource does not exist or does not belong to the caller")
                        .content(content));

        Map<String, ApiResponse> byName = new LinkedHashMap<>();
        responses.forEach(byName::put);
        return byName;
    }
}
