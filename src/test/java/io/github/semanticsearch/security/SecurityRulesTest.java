package io.github.semanticsearch.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Which endpoints answer without credentials when {@code security.auth.enabled} is on.
 *
 * <p>The rest of the suite runs with it off, which is the branch that permits everything, so this
 * is the only place the matcher list is exercised. A route added to the wrong group changes who can
 * reach it and nothing else in the build would notice.
 */
@SpringBootTest(properties = "security.auth.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesTest {

  private static final RequestPostProcessor ADMIN =
      SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin");

  @Autowired private MockMvc mockMvc;

  @Test
  void searchIsPublic() throws Exception {
    mockMvc.perform(get("/api/v1/search").param("query", "anything")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/search/similar/" + UUID.randomUUID())).andExpect(status().isOk());
  }

  @Test
  void theApiDocumentationIsPublic() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    // The address the README and the springdoc config both advertise. It is not
    // under /swagger-ui/, so it needs its own matcher; without one it answers 401
    // and the redirect that would reach the UI never happens.
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }

  @Test
  void livenessIsPublicAndTheRestOfActuatorIsNot() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  }

  @Test
  void writesNeedCredentials() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"content\":\"C\"}"))
        .andExpect(status().isUnauthorized())
        // Without this header a browser never prompts and a client cannot tell
        // which scheme to use.
        .andExpect(header().exists("WWW-Authenticate"));

    mockMvc.perform(post("/api/v1/search/index/rebuild")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/eval/run")).andExpect(status().isUnauthorized());
  }

  @Test
  void writesSucceedWithCredentials() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/documents")
                .with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Authenticated write\",\"content\":\"Created with credentials.\"}"))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/search/index/rebuild").with(ADMIN)).andExpect(status().isOk());
  }

  @Test
  void theBundledUiIsBehindAuthenticationWhenAuthenticationIsOn() throws Exception {
    // The bundled UI sits behind the same basic auth as the write endpoints.
    // Basic auth sends a challenge, so a browser prompts and then loads the page.
    mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/").with(ADMIN)).andExpect(status().isOk());
  }
}
