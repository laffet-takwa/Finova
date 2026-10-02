package com.finova.gateway.config;

import com.finova.gateway.documentation.GatewayRouteCatalog.GatewayRoute;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the aggregated OpenAPI view of the platform: one entry per gateway
 * route, each linking to the OpenAPI document and Swagger UI of the owning
 * service, proxied under {@code /docs/{service}/}.
 * <p>
 * The {@code bearerAuth} security scheme declared here is what makes Swagger UI
 * show a working Authorize button.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "bearerAuth";

    private static final String BASE_DESCRIPTION = """
            Central entry point of the Finova platform. Every path below is routed to the owning \
            service through Eureka load balancing. Authenticate once against /api/auth/login, then \
            send `Authorization: Bearer <accessToken>` on every other call; the gateway validates \
            the token and forwards the caller identity as X-User-Id / X-User-Email / X-User-Role.

            The full contract of each service is proxied from this host, for example \
            /docs/transaction-service/swagger-ui/index.html.
            """;

    @Bean
    public OpenAPI finovaGatewayOpenApi() {
        return aggregatedDocument(List.of());
    }

    /**
     * @param routes the live gateway routes, read from the routing table so the
     *               document can never drift away from {@code application.yml}
     */
    public static OpenAPI aggregatedDocument(List<GatewayRoute> routes) {
        List<GatewayRoute> serviceRoutes = routes == null ? List.of() : routes.stream()
                .filter(GatewayRoute::isServiceRoute)
                .toList();
        List<String> services = new ArrayList<>();
        Paths paths = new Paths();
        for (GatewayRoute route : serviceRoutes) {
            paths.addPathItem(route.path(), pathItem(route));
            if (!services.contains(route.target())) {
                services.add(route.target());
            }
        }
        return new OpenAPI()
                .info(info(services))
                .externalDocs(new ExternalDocumentation()
                        .description("Finova service documentation index")
                        .url("/docs"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description("Access token issued by POST /api/auth/login.")))
                .tags(List.of(
                        new Tag().name("gateway").description("Platform operations"),
                        new Tag().name("user-service").description("Identity, authentication, audit trail"),
                        new Tag().name("account-service").description("Accounts and balance read projection"),
                        new Tag().name("transaction-service").description("Transfers, ledger and settlement"),
                        new Tag().name("fraud-service").description("Fraud alerts and risk scoring (ADMIN)"),
                        new Tag().name("notification-service").description("In-app notifications")))
                .paths(paths);
    }

    private static Info info(List<String> services) {
        String description = services.isEmpty()
                ? BASE_DESCRIPTION
                : BASE_DESCRIPTION + "\n\nProxied service documents: "
                + String.join(", ", services.stream()
                .map(name -> "[`" + name + "`](/docs/" + name + "/swagger-ui/index.html)")
                .toList()) + ".";
        return new Info()
                .title("Finova API Gateway")
                .description(description)
                .version("1.0.0")
                .contact(new Contact().name("Finova Engineering").email("engineering@finova.dev"));
    }

    private static PathItem pathItem(GatewayRoute route) {
        return new PathItem()
                .get(operation("get", route))
                .post(operation("post", route))
                .summary("Proxied to " + route.target());
    }

    private static Operation operation(String method, GatewayRoute route) {
        return new Operation()
                .operationId(route.id() + "-" + method)
                .summary("Routed to " + route.target())
                .description("Forwarded verbatim to `" + route.path() + "` over `lb://" + route.target()
                        + "` with the caller identity in the X-User-Id / X-User-Email / X-User-Role headers. "
                        + "The full contract of this resource is served by the service itself: "
                        + route.docsUrl() + " (Swagger UI: " + route.swaggerUiUrl() + ").")
                .tags(List.of(route.target()))
                .responses(responses());
    }

    private static ApiResponses responses() {
        return new ApiResponses()
                .addApiResponse("200", new ApiResponse()
                        .description("Response produced by the downstream service")
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(new StringSchema()))))
                .addApiResponse("401", new ApiResponse().description("Missing, invalid or expired access token"))
                .addApiResponse("403", new ApiResponse().description("Administrator role required"))
                .addApiResponse("404", new ApiResponse().description("No gateway route matches this path"))
                .addApiResponse("429", new ApiResponse().description("Rate limit exceeded, see Retry-After"))
                .addApiResponse("503", new ApiResponse()
                        .description("No healthy instance of the target service is registered"));
    }
}
