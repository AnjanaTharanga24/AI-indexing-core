package com.aura.ai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Bean
    public OpenAPI openAPI() {
        String authUrl = authServerUrl + "realms/" + realm + "/protocol/openid-connect/auth";
        String tokenUrl = authServerUrl + "realms/" + realm + "/protocol/openid-connect/token";

        return new OpenAPI()
                .info(new Info()
                        .title("AuraDocs AI Service API")
                        .description("AI-powered document analysis service for AuraDocs V4. Analyzes document images using Ollama vision models and generates structured template profiles.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("OAuth2"))
                .components(new Components()
                        .addSecuritySchemes("OAuth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authUrl)
                                                .tokenUrl(tokenUrl)))));
    }
}
