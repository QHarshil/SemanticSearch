package io.github.semanticsearch.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The bundled UI is served from the jar, so how unmatched paths resolve is part of the
 * application's behaviour.
 *
 * <p>Nothing covered this before, and it was completely broken: the SPA fallback forwarded matching
 * paths to /index.html using a pattern that also matched index.html and every file under /assets,
 * so the forward target forwarded to itself. Loading the UI from the packaged jar produced a
 * StackOverflowError, and the tests could not have noticed because none of them made an HTTP
 * request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticUiRoutingTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void rootServesTheApplicationShell() throws Exception {
    // Spring Boot's welcome-page mapping forwards / to index.html. MockMvc records
    // the forward rather than following it, so the target is what to assert here;
    // the served body is covered by the client-side route cases below.
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl(
                "index.html"));
  }

  @Test
  void clientSideRoutesFallBackToTheShell() throws Exception {
    // These paths have no server-side mapping; the React router owns them.
    for (String route : new String[] {"/search", "/documents", "/about", "/documents/nested"}) {
      mockMvc
          .perform(get(route))
          .andExpect(status().isOk())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("<div id=\"root\">")));
    }
  }

  @Test
  void indexHtmlItselfIsServedRatherThanForwardedInACircle() throws Exception {
    mockMvc.perform(get("/index.html")).andExpect(status().isOk());
  }

  @Test
  void aMissingAssetIs404NotTheShell() throws Exception {
    // Answering 200 with HTML for a missing script leaves the browser reporting a
    // syntax error instead of a missing file.
    mockMvc.perform(get("/assets/does-not-exist.js")).andExpect(status().isNotFound());
  }

  @Test
  void unmappedApiPathsAreNotSwallowedByTheFallback() throws Exception {
    mockMvc.perform(get("/api/v1/no-such-endpoint")).andExpect(status().isNotFound());
  }

  @Test
  void healthIsUpInTheDefaultStubConfiguration() throws Exception {
    // The autoconfigured Elasticsearch indicator pings a real cluster, which does
    // not exist when the in-memory index is used - and that is the default. Left
    // enabled it reported DOWN out of the box, which would fail a readiness probe
    // and the container health check on a service that is working perfectly well.
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")));
  }
}
