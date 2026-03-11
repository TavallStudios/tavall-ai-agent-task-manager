package com.agenttaskmanager.app.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenttaskmanager.app.model.PromptMessage;
import com.agenttaskmanager.app.model.PromptRequestDetail;
import com.agenttaskmanager.app.model.PromptRequestFull;
import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.model.PromptRun;
import com.agenttaskmanager.app.service.PromptRequestService;
import com.agenttaskmanager.app.service.RepoCatalogService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    PromptRequestApiController.class,
    RepoCatalogApiController.class,
    ApiExceptionHandler.class
})
@AutoConfigureMockMvc(addFilters = false)
class PromptRequestApiControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldListPromptRequests() throws Exception {
    mockMvc.perform(get("/api/prompt-requests").with(user("tester").roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].requestId").value("pr_123"))
        .andExpect(jsonPath("$.items[0].status").value("queued"));
  }

  @Test
  void shouldCreatePromptRequest() throws Exception {
    mockMvc.perform(post("/api/prompt-requests")
            .with(csrf())
            .principal(() -> "tester")
            .contentType("application/json")
            .content("""
                {
                  "repoPath": "/srv/Companions",
                  "bridgeTarget": "local-ide",
                  "executionMode": "edit",
                  "promptText": "Investigate startup race"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value("pr_123"))
        .andExpect(jsonPath("$.requestedBy").value("tester"));
  }

  @Test
  void shouldListKnownRepos() throws Exception {
    mockMvc.perform(get("/api/repos").with(user("tester").roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].displayName").value("Companions"))
        .andExpect(jsonPath("$.items[0].projectKey").value("companions"))
        .andExpect(jsonPath("$.items[0].repoPath").value("/srv/Companions"));
  }

  @Test
  void shouldGetPromptDetail() throws Exception {
    mockMvc.perform(get("/api/prompt-requests/pr_123").with(user("tester").roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.request.requestId").value("pr_123"))
        .andExpect(jsonPath("$.runs[0].bridgeName").value("codex-bridge"))
        .andExpect(jsonPath("$.messages[0].body").value("Working..."));
  }

  @TestConfiguration
  static class StubConfig {

    @Bean
    @Primary
    PromptRequestService promptRequestService() {
      return new PromptRequestService(null, null) {
        @Override
        public List<PromptRequestSummary> list(int limit, String status) {
          return List.of(sampleSummary("pr_123", "tester"));
        }

        @Override
        public PromptRequestSummary create(
            String projectKey,
            String repoPath,
            String bridgeTarget,
            String executionMode,
            String promptText,
            String requestedBy,
            String requestedFrom
        ) {
          return sampleSummary("pr_123", requestedBy);
        }

        @Override
        public PromptRequestDetail getDetail(String requestId) {
          return new PromptRequestDetail(
              new PromptRequestFull(
                  requestId,
                  "companions",
                  "/srv/Companions",
                  "local-ide",
                  "local-ide:/srv/companions",
                  "tester",
                  null,
                  null,
                  "edit",
                  "queued",
                  "Investigate startup race",
                  "Queued from web control plane",
                  OffsetDateTime.parse("2026-03-11T00:00:00Z"),
                  OffsetDateTime.parse("2026-03-11T00:00:00Z"),
                  null
              ),
              List.of(new PromptRun(
                  10L,
                  "session-1",
                  "codex-bridge",
                  "thread-1",
                  "running",
                  null,
                  "Started",
                  OffsetDateTime.parse("2026-03-11T00:00:00Z"),
                  OffsetDateTime.parse("2026-03-11T00:01:00Z"),
                  null
              )),
              List.of(new PromptMessage(
                  20L,
                  10L,
                  "stdout",
                  "codex",
                  "Working...",
                  OffsetDateTime.parse("2026-03-11T00:01:00Z")
              ))
          );
        }

        private PromptRequestSummary sampleSummary(String requestId, String requestedBy) {
          return new PromptRequestSummary(
              requestId,
              "companions",
              "/srv/Companions",
              "local-ide",
              "local-ide:/srv/companions",
              requestedBy,
              null,
              null,
              "edit",
              "queued",
              "Investigate startup race",
              "Queued from web control plane",
              OffsetDateTime.parse("2026-03-11T00:00:00Z"),
              OffsetDateTime.parse("2026-03-11T00:00:00Z"),
              null,
              null,
              null,
              null,
              OffsetDateTime.parse("2026-03-11T00:00:00Z")
          );
        }
      };
    }

    @Bean
    @Primary
    RepoCatalogService repoCatalogService() {
      return new RepoCatalogService(null) {
        @Override
        public List<com.agenttaskmanager.app.model.KnownRepo> listRepos() {
          return List.of(new com.agenttaskmanager.app.model.KnownRepo(
              "Companions",
              "companions",
              "/srv/Companions",
              "remote"
          ));
        }

        @Override
        public com.agenttaskmanager.app.model.KnownRepo requireByPath(String repoPath) {
          return listRepos().getFirst();
        }
      };
    }
  }
}
