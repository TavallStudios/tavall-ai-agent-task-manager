package org.tavall.ai.app.mcp;

import org.tavall.ai.app.config.McpServerProperties;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfiguration {

  @Bean
  public McpJsonMapper mcpJsonMapper(SpringJacksonMcpJsonMapper springJacksonMcpJsonMapper) {
    return springJacksonMcpJsonMapper;
  }

  @Bean
  public HttpServletStreamableServerTransportProvider mcpTransportProvider(
      McpJsonMapper mcpJsonMapper,
      McpServerProperties mcpServerProperties
  ) {
    return HttpServletStreamableServerTransportProvider.builder()
        .jsonMapper(mcpJsonMapper)
        .mcpEndpoint(mcpServerProperties.getEndpoint())
        .build();
  }

  @Bean
  public ServletRegistrationBean<HttpServlet> mcpServletRegistration(
      HttpServletStreamableServerTransportProvider transportProvider,
      McpServerProperties mcpServerProperties
  ) {
    String endpoint = mcpServerProperties.getEndpoint();
    return new ServletRegistrationBean<>(transportProvider, endpoint, endpoint + "/*");
  }

  @Bean(destroyMethod = "close")
  public McpSyncServer mcpSyncServer(
      HttpServletStreamableServerTransportProvider transportProvider,
      McpCatalog mcpCatalog,
      McpJsonMapper mcpJsonMapper
  ) {
    return McpServer.sync(transportProvider)
        .serverInfo("AgentTaskManager MCP", "0.1.0")
        .instructions("Use the orchestration tools to create, validate, review, and summarize task batches.")
        .jsonMapper(mcpJsonMapper)
        .tools(mcpCatalog.toolSpecifications())
        .resources(mcpCatalog.resourceSpecifications())
        .prompts(mcpCatalog.promptSpecifications())
        .build();
  }
}

