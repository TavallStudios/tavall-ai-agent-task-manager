package com.agenttaskmanager.app.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class McpToolHandlerIntegrationAssertions {

  private McpToolHandlerIntegrationAssertions() {
  }

  public static Set<String> handlerToolNames(McpToolProvider provider) {
    return provider.toolSpecifications().stream()
        .map(McpToolHandlerIntegrationAssertions::toolName)
        .collect(Collectors.toSet());
  }

  public static Set<String> serverToolNames(McpSyncServer server) {
    return server.listTools().stream()
        .map(tool -> tool.name())
        .collect(Collectors.toSet());
  }

  public static void assertContainsAll(Set<String> actualNames, String... expectedNames) {
    Arrays.stream(expectedNames).forEach(name -> assertTrue(actualNames.contains(name), "Expected tool " + name));
  }

  private static String toolName(SyncToolSpecification specification) {
    Object tool = invokeNoArg(specification, "tool");
    return (String) invokeNoArg(tool, "name");
  }

  private static Object invokeNoArg(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Failed to inspect MCP tool metadata via method " + methodName, exception);
    }
  }
}
