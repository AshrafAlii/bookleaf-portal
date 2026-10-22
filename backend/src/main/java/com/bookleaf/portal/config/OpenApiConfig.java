package com.bookleaf.portal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 documentation for the whole API.
 *
 * <p>Swagger UI is served at {@code /swagger-ui.html} and the raw spec at
 * {@code /v3/api-docs}, so the API documentation deliverable is generated from
 * the code and can never drift out of date.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookLeafOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BookLeaf Author Support & Communication Portal API")
                        .version("1.0.0")
                        .description("""
                                REST API backing the BookLeaf author support portal.

                                **Authentication** — obtain an access token from `POST /api/v1/auth/login`
                                and send it as `Authorization: Bearer <token>`. Access tokens last 30 minutes;
                                `POST /api/v1/auth/refresh` (using the httpOnly refresh cookie) issues a new one.

                                **Roles** — `AUTHOR` endpoints return only the signed-in author's own data;
                                `ADMIN` endpoints expose the full support queue.
                                """)
                        .contact(new Contact().name("BookLeaf Publishing — Platform Team"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token returned by /api/v1/auth/login")));
    }
}
