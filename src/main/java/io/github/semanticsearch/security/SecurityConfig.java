package io.github.semanticsearch.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Security configuration for the application. Configures CORS, CSRF, and API security settings. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${security.cors.allowed-origins:*}")
  private List<String> allowedOrigins;

  @Value("${security.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
  private List<String> allowedMethods;

  @Value("${security.cors.allowed-headers:*}")
  private List<String> allowedHeaders;

  @Value("${security.cors.max-age:3600}")
  private long maxAge;

  /**
   * Configures security filter chain. Sets up CORS, CSRF, and API endpoint security.
   *
   * @param http HttpSecurity to configure
   * @return Configured SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public endpoints
                    .requestMatchers("/api/v1/search")
                    .permitAll()
                    .requestMatchers("/api/v1/search/similar/**")
                    .permitAll()
                    .requestMatchers("/api/v1/documents/**")
                    .permitAll() // For demo purposes

                    // Swagger/OpenAPI endpoints
                    .requestMatchers("/swagger-ui/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()

                    // Actuator endpoints
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/actuator/info")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .authenticated()

                    // Admin endpoints
                    .requestMatchers("/api/v1/search/index/rebuild")
                    .authenticated()

                    // All other endpoints require authentication
                    .anyRequest()
                    .authenticated())
        .build();
  }

  /**
   * Configures CORS settings.
   *
   * @return CorsConfigurationSource with configured CORS settings
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(allowedMethods);
    configuration.setAllowedHeaders(allowedHeaders);
    configuration.setMaxAge(maxAge);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
