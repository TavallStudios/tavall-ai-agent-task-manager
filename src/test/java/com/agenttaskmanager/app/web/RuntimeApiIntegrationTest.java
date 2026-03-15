package com.agenttaskmanager.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenttaskmanager.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {
    "app.operator.ide-health-url=http://127.0.0.1:9/healthz"
})
class RuntimeApiIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldExposeOperatorAccess() throws Exception {
    mockMvc.perform(get("/api/runtime/access"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repoRoots[0]").value("/srv"))
        .andExpect(jsonPath("$.failoverSteps[0]").value(
            "Open the browser IDE first when the remote editor transport starts dropping."
        ))
        .andExpect(jsonPath("$.tools[0].toolId").value("dashboard"))
        .andExpect(jsonPath("$.tools[1].toolId").value("browser-ide"))
        .andExpect(jsonPath("$.tools[4].command").value("ssh novus-remote"));
  }
}
