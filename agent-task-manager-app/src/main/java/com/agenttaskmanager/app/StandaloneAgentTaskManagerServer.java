package com.agenttaskmanager.app;

import com.agenttaskmanager.app.config.McpServerProperties;
import com.agenttaskmanager.app.config.SecurityProperties;
import com.agenttaskmanager.app.mcp.McpCatalog;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

public final class StandaloneAgentTaskManagerServer implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneAgentTaskManagerServer.class);
  private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

  private final ConfigurableApplicationContext context;
  private final Tomcat tomcat;
  private final McpSyncServer mcpServer;
  private final HttpServletStreamableServerTransportProvider transportProvider;
  private final String endpointPath;

  private StandaloneAgentTaskManagerServer(
      ConfigurableApplicationContext context,
      Tomcat tomcat,
      McpSyncServer mcpServer,
      HttpServletStreamableServerTransportProvider transportProvider,
      String endpointPath
  ) {
    this.context = context;
    this.tomcat = tomcat;
    this.mcpServer = mcpServer;
    this.transportProvider = transportProvider;
    this.endpointPath = endpointPath;
  }

  public static void main(String[] args) {
    try (StandaloneAgentTaskManagerServer server = start(args)) {
      LOGGER.info("AgentTaskManager standalone MCP runtime listening on {}", server.endpointUrl());
      CountDownLatch latch = new CountDownLatch(1);
      Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown, "agent-task-manager-shutdown"));
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Standalone MCP runtime interrupted.", exception);
    }
  }

  public static StandaloneAgentTaskManagerServer start(String... args) {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(AgentTaskManagerApplication.class)
        .web(WebApplicationType.NONE)
        .run(args);

    try {
      return start(context);
    } catch (RuntimeException exception) {
      context.close();
      throw exception;
    }
  }

  static StandaloneAgentTaskManagerServer start(ConfigurableApplicationContext context) {
    Environment environment = context.getEnvironment();
    McpJsonMapper jsonMapper = context.getBean("mcpJsonMapper", McpJsonMapper.class);
    McpCatalog catalog = context.getBean(McpCatalog.class);
    McpServerProperties mcpServerProperties = context.getBean(McpServerProperties.class);
    SecurityProperties securityProperties = context.getBean(SecurityProperties.class);

    String endpoint = normalizeServletPath(mcpServerProperties.getEndpoint());
    HttpServletStreamableServerTransportProvider transportProvider =
        HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint(endpoint)
            .build();

    McpSyncServer mcpServer = McpServer.sync(transportProvider)
        .serverInfo("AgentTaskManager MCP", "0.1.0")
        .instructions("Use the orchestration tools to create, validate, review, and summarize task batches.")
        .jsonMapper(jsonMapper)
        .tools(catalog.toolSpecifications())
        .resources(catalog.resourceSpecifications())
        .prompts(catalog.promptSpecifications())
        .build();

    Tomcat tomcat = new Tomcat();
    tomcat.setBaseDir(createBaseDir().toString());

    int port = environment.getProperty("server.port", Integer.class, 9000);
    String address = environment.getProperty("server.address", "0.0.0.0");
    String contextPath = normalizeContextPath(environment.getProperty("server.servlet.context-path", ""));

    tomcat.setPort(port);
    tomcat.getConnector();
    tomcat.getConnector().setProperty("address", address);

    Context servletContext = tomcat.addContext(
        contextPath,
        createBaseDir().toAbsolutePath().toString()
    );

    registerMcpServlet(servletContext, endpoint, transportProvider);
    registerMcpAuthFilter(servletContext, endpoint, securityProperties);

    try {
      tomcat.start();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to start standalone MCP runtime.", exception);
    }

    logGeneratedPasswordIfNeeded(securityProperties);
    return new StandaloneAgentTaskManagerServer(context, tomcat, mcpServer, transportProvider, contextPath + endpoint);
  }

  public int port() {
    return tomcat.getConnector().getLocalPort();
  }

  public String endpointUrl() {
    return "http://127.0.0.1:" + port() + endpointPath;
  }

  @Override
  public void close() {
    try {
      mcpServer.close();
    } catch (Exception ignored) {
      LOGGER.debug("Ignoring MCP server close failure.", ignored);
    }
    try {
      tomcat.stop();
      tomcat.destroy();
    } catch (Exception exception) {
      LOGGER.warn("Failed to stop embedded Tomcat cleanly.", exception);
    }
    transportProvider.close();
    context.close();
  }

  private static void registerMcpServlet(
      Context servletContext,
      String endpoint,
      HttpServlet transportProvider
  ) {
    Tomcat.addServlet(servletContext, "agentTaskManagerMcp", transportProvider);
    servletContext.addServletMappingDecoded(endpoint, "agentTaskManagerMcp");
    servletContext.addServletMappingDecoded(endpoint + "/*", "agentTaskManagerMcp");
  }

  private static void registerMcpAuthFilter(
      Context servletContext,
      String endpoint,
      SecurityProperties securityProperties
  ) {
    Filter filter = new StandaloneMcpSecurityFilter(securityProperties);
    FilterDef filterDef = new FilterDef();
    filterDef.setFilterName("agentTaskManagerMcpSecurity");
    filterDef.setFilter(filter);
    filterDef.setFilterClass(filter.getClass().getName());
    servletContext.addFilterDef(filterDef);

    FilterMap filterMap = new FilterMap();
    filterMap.setFilterName("agentTaskManagerMcpSecurity");
    filterMap.addURLPattern(endpoint);
    filterMap.addURLPattern(endpoint + "/*");
    servletContext.addFilterMap(filterMap);
  }

  private static Path createBaseDir() {
    try {
      return Files.createTempDirectory("agent-task-manager-standalone");
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to create standalone server base directory.", exception);
    }
  }

  private static String normalizeServletPath(String endpoint) {
    if (endpoint == null || endpoint.isBlank() || "/".equals(endpoint)) {
      return "/mcp";
    }
    String normalized = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
  }

  private static String normalizeContextPath(String contextPath) {
    if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
      return "";
    }
    String normalized = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
  }

  private static void logGeneratedPasswordIfNeeded(SecurityProperties securityProperties) {
    if (securityProperties.isMcpNoAuthEnabled()) {
      return;
    }
    if (securityProperties.getPassword() != null && !securityProperties.getPassword().isBlank()) {
      return;
    }
    String generatedPassword = generatePassword();
    securityProperties.setPassword(generatedPassword);
    LOGGER.warn(
        "AGENT_TASK_MANAGER_PASSWORD was not set. Generated startup password for user '{}': {}",
        securityProperties.getUsername(),
        generatedPassword
    );
  }

  private static String generatePassword() {
    SecureRandom random = new SecureRandom();
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < 20; index++) {
      builder.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
    }
    return builder.toString();
  }

  static final class StandaloneMcpSecurityFilter implements Filter {

    private final SecurityProperties securityProperties;

    private StandaloneMcpSecurityFilter(SecurityProperties securityProperties) {
      this.securityProperties = securityProperties;
    }

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request,
        jakarta.servlet.ServletResponse response,
        jakarta.servlet.FilterChain chain
    ) throws IOException, jakarta.servlet.ServletException {
      jakarta.servlet.http.HttpServletRequest httpRequest =
          (jakarta.servlet.http.HttpServletRequest) request;
      jakarta.servlet.http.HttpServletResponse httpResponse =
          (jakarta.servlet.http.HttpServletResponse) response;

      if (securityProperties.isMcpNoAuthEnabled() || proxyHeaderPresent(httpRequest) || basicAuthValid(httpRequest)) {
        chain.doFilter(request, response);
        return;
      }

      httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"AgentTaskManager MCP\"");
      httpResponse.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean proxyHeaderPresent(jakarta.servlet.http.HttpServletRequest request) {
      if (!securityProperties.isProxyAuthEnabled()) {
        return false;
      }
      String headerValue = request.getHeader(securityProperties.getProxyAuthHeader());
      return headerValue != null && !headerValue.isBlank();
    }

    private boolean basicAuthValid(jakarta.servlet.http.HttpServletRequest request) {
      String authorization = request.getHeader("Authorization");
      if (authorization == null || !authorization.startsWith("Basic ")) {
        return false;
      }
      String decoded;
      try {
        decoded = new String(Base64.getDecoder().decode(authorization.substring("Basic ".length())));
      } catch (IllegalArgumentException exception) {
        return false;
      }
      int separator = decoded.indexOf(':');
      if (separator < 0) {
        return false;
      }
      String username = decoded.substring(0, separator);
      String password = decoded.substring(separator + 1);
      return securityProperties.getUsername().equals(username)
          && securityProperties.getPassword() != null
          && securityProperties.getPassword().equals(password);
    }
  }
}
