package io.github.semanticsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** The OpenAPI document served at {@code /v3/api-docs} and rendered at {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

  @Value("${spring.application.name:Semantic Search Java}")
  private String applicationName;

  @Value("${spring.application.description:Semantic Search Microservice in Java}")
  private String applicationDescription;

  @Value("${spring.application.version:1.0.0}")
  private String applicationVersion;

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer-key",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .info(
            new Info()
                .title(applicationName)
                .description(applicationDescription)
                .version(applicationVersion)
                .license(
                    new License().name("MIT License").url("https://opensource.org/licenses/MIT")));
  }
}
