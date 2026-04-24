package org.tavall.ai.app.security;

import org.tavall.ai.app.config.SecurityProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class McpSecurityFilter implements Filter {

  private final SecurityProperties securityProperties;
  private final org.tavall.ai.app.security.McpApiKeyAuthenticationService apiKeyAuthenticationService;
  private final org.tavall.ai.app.security.AuthenticatedClientContextHolder contextHolder;

  public McpSecurityFilter(
      SecurityProperties securityProperties,
      org.tavall.ai.app.security.McpApiKeyAuthenticationService apiKeyAuthenticationService,
      org.tavall.ai.app.security.AuthenticatedClientContextHolder contextHolder
  ) {
    this.securityProperties = securityProperties;
    this.apiKeyAuthenticationService = apiKeyAuthenticationService;
    this.contextHolder = contextHolder;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    try {
      Optional<org.tavall.ai.app.security.AuthenticatedClientContext> authenticated = authenticate(httpRequest);
      if (securityProperties.isMcpNoAuthEnabled()) {
        authenticated.ifPresent(contextHolder::set);
        chain.doFilter(request, response);
        return;
      }
      if (authenticated.isPresent()) {
        contextHolder.set(authenticated.get());
        chain.doFilter(request, response);
        return;
      }
      httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"AgentTaskManager MCP\"");
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    } finally {
      contextHolder.clear();
    }
  }

  private Optional<org.tavall.ai.app.security.AuthenticatedClientContext> authenticate(HttpServletRequest request) {
    Optional<org.tavall.ai.app.security.AuthenticatedClientContext> apiKeyContext = authenticateApiKey(request);
    if (apiKeyContext.isPresent()) {
      return apiKeyContext;
    }
    if (securityProperties.isProxyAuthEnabled()) {
      String headerValue = request.getHeader(securityProperties.getProxyAuthHeader());
      if (headerValue != null && !headerValue.isBlank()) {
        return Optional.of(new org.tavall.ai.app.security.AuthenticatedClientContext(
            "proxy-header",
            headerValue.strip(),
            "",
            "proxy",
            headerValue.strip(),
            "",
            List.of("proxy")
        ));
      }
    }
    return authenticateBasicAuth(request);
  }

  private Optional<org.tavall.ai.app.security.AuthenticatedClientContext> authenticateApiKey(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return apiKeyAuthenticationService.authenticate(authorization.substring("Bearer ".length()));
    }
    String rawHeader = request.getHeader(securityProperties.getApiKeyHeader());
    return apiKeyAuthenticationService.authenticate(rawHeader);
  }

  private Optional<org.tavall.ai.app.security.AuthenticatedClientContext> authenticateBasicAuth(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Basic ")) {
      return Optional.empty();
    }
    String decoded;
    try {
      decoded = new String(Base64.getDecoder().decode(authorization.substring("Basic ".length())), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
    int separator = decoded.indexOf(':');
    if (separator < 0) {
      return Optional.empty();
    }
    String username = decoded.substring(0, separator);
    String password = decoded.substring(separator + 1);
    if (!securityProperties.getUsername().equals(username)) {
      return Optional.empty();
    }
    if (securityProperties.getPassword() == null || !securityProperties.getPassword().equals(password)) {
      return Optional.empty();
    }
    return Optional.of(new org.tavall.ai.app.security.AuthenticatedClientContext(
        "basic-auth",
        username,
        "",
        "local-basic",
        username,
        "",
        List.of("operator")
    ));
  }
}

