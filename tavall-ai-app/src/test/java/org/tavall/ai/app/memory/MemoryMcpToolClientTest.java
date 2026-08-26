package org.tavall.ai.app.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryMcpToolClientTest {

  @Test
  void shouldNegotiateProtocolAndReadStreamableHttpJson() throws Exception {
    try (MemoryMcpTestServer server = new MemoryMcpTestServer(new ObjectMapper())) {
      server.setResponseMode(MemoryMcpTestServer.ResponseMode.SSE);
      var result = client().callTool(server.endpoint(), "", Duration.ofSeconds(2), "query_graph", Map.of());

      assertEquals("ok", result.path("content").get(0).path("text").asText());
      assertEquals(2, server.protocolVersions().size());
      assertEquals("2025-06-18", server.protocolVersions().getFirst());
    }
  }

  @Test
  void shouldFailOnHttpFailuresMalformedJsonEmptyResponsesAndToolErrors() throws Exception {
    for (MemoryMcpTestServer.ResponseMode mode : new MemoryMcpTestServer.ResponseMode[] {
        MemoryMcpTestServer.ResponseMode.HTTP_500,
        MemoryMcpTestServer.ResponseMode.INVALID_JSON,
        MemoryMcpTestServer.ResponseMode.EMPTY,
        MemoryMcpTestServer.ResponseMode.TOOL_ERROR,
        MemoryMcpTestServer.ResponseMode.MISMATCHED_ID,
        MemoryMcpTestServer.ResponseMode.STALE_SESSION
    }) {
      try (MemoryMcpTestServer server = new MemoryMcpTestServer(new ObjectMapper())) {
        server.setResponseMode(mode);
        assertThrows(
            IllegalStateException.class,
            () -> client().callTool(server.endpoint(), "", Duration.ofMillis(200), "query_graph", Map.of()),
            mode.name()
        );
      }
    }
  }

  @Test
  void shouldTurnTimeoutIntoAVisibleFailure() throws Exception {
    try (MemoryMcpTestServer server = new MemoryMcpTestServer(new ObjectMapper())) {
      server.setResponseMode(MemoryMcpTestServer.ResponseMode.TIMEOUT);
      assertThrows(
          IllegalStateException.class,
          () -> client().callTool(server.endpoint(), "", Duration.ofMillis(50), "query_graph", Map.of())
      );
    }
  }

  private MemoryMcpToolClient client() {
    return new MemoryMcpToolClient(new ObjectMapper());
  }
}
