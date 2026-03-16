package com.agenttaskmanager.app.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DownstreamMcpToolClientService {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private final McpJsonMapper mcpJsonMapper;
  private final McpServerProcessConfigurationService processConfigurationService;

  public DownstreamMcpToolClientService(
      McpJsonMapper mcpJsonMapper,
      McpServerProcessConfigurationService processConfigurationService
  ) {
    this.mcpJsonMapper = mcpJsonMapper;
    this.processConfigurationService = processConfigurationService;
  }

  public List<DownstreamMcpToolResult> callTools(String projectKey, List<DownstreamMcpToolCall> calls) {
    if (calls == null || calls.isEmpty()) {
      return List.of();
    }

    ExecutorService executor = Executors.newFixedThreadPool(Math.min(calls.size(), 4));
    try {
      List<CompletableFuture<DownstreamMcpToolResult>> futures = calls.stream()
          .map(call -> CompletableFuture.supplyAsync(() -> callTool(projectKey, call), executor))
          .toList();
      return futures.stream()
          .map(CompletableFuture::join)
          .toList();
    } finally {
      executor.shutdownNow();
    }
  }

  public DownstreamMcpToolResult callTool(String projectKey, DownstreamMcpToolCall call) {
    long startedAt = System.nanoTime();
    StringBuilder stderr = new StringBuilder();
    try {
      McpServerProcessConfiguration configuration = processConfigurationService.resolve(call.serverName(), projectKey);
      StdioClientTransport transport = new StdioClientTransport(serverParameters(configuration, call), mcpJsonMapper);
      transport.setStdErrorHandler(line -> appendLine(stderr, line));

      try (McpSyncClient client = McpClient.sync(transport)
          .requestTimeout(TIMEOUT)
          .initializationTimeout(TIMEOUT)
          .clientInfo(new Implementation("AgentTaskManager Harness", "0.1.0"))
          .build()) {
        client.initialize();
        CallToolResult result = client.callTool(new CallToolRequest(call.toolName(), call.arguments()));
        return new DownstreamMcpToolResult(
            call.key(),
            call.serverName(),
            call.toolName(),
            Boolean.TRUE.equals(result.isError()) ? "error" : "completed",
            result.structuredContent(),
            textContent(result),
            stderr.toString(),
            null,
            durationMs(startedAt)
        );
      }
    } catch (Exception exception) {
      return new DownstreamMcpToolResult(
          call.key(),
          call.serverName(),
          call.toolName(),
          "error",
          null,
          null,
          stderr.toString(),
          exception.getMessage(),
          durationMs(startedAt)
      );
    }
  }

  private ServerParameters serverParameters(McpServerProcessConfiguration configuration, DownstreamMcpToolCall call) {
    ServerParameters.Builder builder = ServerParameters.builder(configuration.command());
    List<String> args = new ArrayList<>(configuration.args());
    if ("filesystem".equals(call.serverName())) {
      args.addAll(filesystemRoots(call.arguments()));
    }
    if (!args.isEmpty()) {
      builder.args(args);
    }
    if (!configuration.env().isEmpty()) {
      builder.env(configuration.env());
    }
    return builder.build();
  }

  private List<String> filesystemRoots(Map<String, Object> arguments) {
    LinkedHashSet<String> roots = new LinkedHashSet<>();
    Object singlePath = arguments.get("path");
    if (singlePath instanceof String path && !path.isBlank()) {
      roots.add(Path.of(path).toAbsolutePath().normalize().toString());
    }
    Object multiplePaths = arguments.get("paths");
    if (multiplePaths instanceof List<?> list) {
      list.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .filter(path -> !path.isBlank())
          .map(path -> Path.of(path).toAbsolutePath().normalize().toString())
          .forEach(roots::add);
    }
    return roots.stream().toList();
  }

  private String textContent(CallToolResult result) {
    return result.content().stream()
        .filter(TextContent.class::isInstance)
        .map(TextContent.class::cast)
        .map(TextContent::text)
        .collect(Collectors.joining("\n"));
  }

  private void appendLine(StringBuilder sink, String line) {
    if (sink.length() >= 4000) {
      return;
    }
    if (!sink.isEmpty()) {
      sink.append('\n');
    }
    sink.append(line);
  }

  private long durationMs(long startedAt) {
    return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
  }
}
