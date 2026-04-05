package com.agenttaskmanager.app.security;

import com.agenttaskmanager.app.config.SecurityProperties;
import com.agenttaskmanager.app.persistence.postgres.McpApiKeyRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class McpApiKeyBootstrapService implements ApplicationRunner {

  private final SecurityProperties securityProperties;
  private final McpApiKeyAuthenticationService authenticationService;
  private final McpApiKeyRepository apiKeyRepository;

  public McpApiKeyBootstrapService(
      SecurityProperties securityProperties,
      McpApiKeyAuthenticationService authenticationService,
      McpApiKeyRepository apiKeyRepository
  ) {
    this.securityProperties = securityProperties;
    this.authenticationService = authenticationService;
    this.apiKeyRepository = apiKeyRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (SecurityProperties.ApiKeySeed seed : securityProperties.getBootstrapApiKeys()) {
      if (seed.getToken() == null || seed.getToken().isBlank()) {
        continue;
      }
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("source", "bootstrap-config");
      apiKeyRepository.upsertBootstrapKey(
          seed.getApiKeyId().isBlank() ? "key-" + authenticationService.hash(seed.getToken()).substring(0, 12) : seed.getApiKeyId(),
          seed.getDisplayName().isBlank() ? "agent-task-manager-client" : seed.getDisplayName(),
          authenticationService.hash(seed.getToken()),
          blank(seed.getWorkspaceId()),
          blank(seed.getUserId()),
          blank(seed.getProjectId()),
          seed.getRoles().isEmpty() ? List.of("memory-client") : seed.getRoles(),
          metadata
      );
    }
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
