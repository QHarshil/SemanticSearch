package io.github.semanticsearch.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled React app and lets it own client-side routing.
 *
 * <p>Any request that does not match a real file falls back to index.html, so deep links such as
 * /search are handled by the router instead of 404ing.
 *
 * <p>Implemented as a resource resolver rather than a view controller forwarding to
 * "forward:/index.html". A forward re-enters request mapping, so any pattern broad enough to catch
 * client-side routes also catches index.html and the files under /assets, and the forward target
 * forwards to itself until the stack overflows. Resolving resources directly has no such loop.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final String[] SERVER_PREFIXES = {
    "api/", "actuator/", "v3/", "swagger-ui", "webjars/"
  };

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String resourcePath, Resource location)
                  throws IOException {
                Resource requested = location.createRelative(resourcePath);
                if (requested.exists() && requested.isReadable()) {
                  return requested;
                }
                if (isServerPath(resourcePath) || looksLikeAFile(resourcePath)) {
                  // Let these 404 properly. Returning the SPA shell for a missing
                  // asset or an unmapped API route answers 200 with HTML, which is
                  // considerably harder to debug than a 404.
                  return null;
                }
                return new ClassPathResource("/static/index.html");
              }
            });
  }

  private static boolean isServerPath(String resourcePath) {
    for (String prefix : SERVER_PREFIXES) {
      if (resourcePath.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** A dot in the last segment means an asset was requested, not a client-side route. */
  private static boolean looksLikeAFile(String resourcePath) {
    int lastSlash = resourcePath.lastIndexOf('/');
    return resourcePath.indexOf('.', lastSlash + 1) >= 0;
  }
}
