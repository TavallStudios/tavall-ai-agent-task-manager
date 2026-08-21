package org.tavall.ai.app.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.ai.app.config.ChatGPTMcpGatewayProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatGPTMcpUnixSocketGatewayTest {

  @Test
  void shouldServeOneModernMcpCatalogOverTheUnixSocket(@TempDir Path tempDir) {
    Path socketPath = tempDir.resolve("chatgpt-mcp.sock");
    AtomicInteger openedSessions = new AtomicInteger();
    AtomicInteger closedSessions = new AtomicInteger();
    ChatGPTMcpGatewayProperties properties = enabledProperties(tempDir, socketPath);
    ChatGPTMcpUnixSocketGateway gateway = new ChatGPTMcpUnixSocketGateway(
        properties,
        fixtureFactory(openedSessions, closedSessions),
        new SpringJacksonMcpJsonMapper(new ObjectMapper().findAndRegisterModules())
    );

    gateway.start();
    try (McpSyncClient client = newClient(socketPath)) {
      McpSchema.InitializeResult initialization = client.initialize();

      assertEquals("2025-11-25", initialization.protocolVersion());
      assertEquals("Tavall Cloud ChatGPT Gateway", initialization.serverInfo().name());
      assertTrue(initialization.capabilities().tools().listChanged());
      assertTrue(client.listTools().tools().stream().anyMatch(tool -> "cloud_fixture_status".equals(tool.name())));

      McpSchema.CallToolResult result = client.callTool(
          new McpSchema.CallToolRequest("cloud_fixture_status", Map.of())
      );
      assertEquals(false, result.isError());
      assertEquals("ready", ((Map<?, ?>) result.structuredContent()).get("status"));
    } finally {
      gateway.close();
    }

    assertEquals(1, openedSessions.get());
    assertEquals(1, closedSessions.get());
    assertTrue(Files.notExists(socketPath));
  }

  private ChatGPTMcpGatewayProperties enabledProperties(Path tempDir, Path socketPath) {
    ChatGPTMcpGatewayProperties properties = new ChatGPTMcpGatewayProperties();
    properties.setEnabled(true);
    properties.setSocketPath(socketPath);
    try {
      properties.setSocketGroup(Files.readAttributes(tempDir, PosixFileAttributes.class).group().getName());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to resolve the temporary directory group", exception);
    }
    return properties;
  }

  private McpGatewayCatalogSessionFactory fixtureFactory(
      AtomicInteger openedSessions,
      AtomicInteger closedSessions
  ) {
    return () -> {
      openedSessions.incrementAndGet();
      SyncToolSpecification tool = new SyncToolSpecification(
          McpSchema.Tool.builder()
              .name("cloud_fixture_status")
              .description("Return the verified fixture status.")
              .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()))
              .build(),
          (exchange, request) -> new McpSchema.CallToolResult(
              List.of(new McpSchema.TextContent("ready")),
              false,
              Map.of("status", "ready"),
              null
          )
      );
      return new McpGatewayCatalogSession(
          "Tavall Cloud ChatGPT Gateway",
          "1.1.2-catalog-59-fixture",
          "Serve the fixture Cloud catalog.",
          List.of(tool),
          List.of(),
          ignored -> {
          },
          closedSessions::incrementAndGet
      );
    };
  }

  private McpSyncClient newClient(Path socketPath) {
    StdioClientTransport transport = new StdioClientTransport(
        ServerParameters.builder("/usr/bin/nc").args(List.of("-U", socketPath.toString())).build(),
        new SpringJacksonMcpJsonMapper(new ObjectMapper().findAndRegisterModules())
    );
    return McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(10))
        .initializationTimeout(Duration.ofSeconds(10))
        .clientInfo(new McpSchema.Implementation("gateway-integration-test", "1.0.0"))
        .build();
  }
}
