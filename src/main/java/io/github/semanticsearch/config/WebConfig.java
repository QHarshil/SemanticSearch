package io.github.semanticsearch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Routes root and non-API paths to the bundled React single-page app so the UI
 * is served at "/" for demos and recruiters.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // Root goes to the prebuilt UI in static/
    registry.addViewController("/").setViewName("forward:/index.html");
    // SPA fallback: any path without an extension and not under /api or /actuator
    registry
        .addViewController("/{path:^(?!api|actuator|swagger-ui|v3).*}")
        .setViewName("forward:/index.html");
    registry
        .addViewController("/{path:^(?!api|actuator|swagger-ui|v3).*}/{subpath:^(?!api|actuator|swagger-ui|v3).*}")
        .setViewName("forward:/index.html");
  }
}
