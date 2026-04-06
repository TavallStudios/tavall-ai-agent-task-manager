package org.tavall.ai.app.security;

import org.tavall.ai.app.persistence.postgres.McpApiKeyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class McpApiKeyAuthenticationService {

  private final McpApiKeyRepository apiKeyRepository;

  public McpApiKeyAuthenticationService(McpApiKeyRepository apiKeyRepository) {
    this.apiKeyRepository = apiKeyRepository;
  }

  public Optional<AuthenticatedClientContext> authenticate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    String tokenHash = hash(rawToken.strip());
    return apiKeyRepository.findActiveByHash(tokenHash)
        .map(record -> {
          apiKeyRepository.touchLastUsed(record.apiKeyId());
          List<String> roles = record.roles() == null ? List.of() : record.roles();
          return new AuthenticatedClientContext(
              "api-key",
              record.displayName(),
              record.apiKeyId(),
              record.workspaceId(),
              record.userId(),
              record.projectId(),
              roles
          );
        });
  }

  public String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to hash API key token.", exception);
    }
  }
}

