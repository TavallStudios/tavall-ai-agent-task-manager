package org.tavall.ai.app.mcp;

import org.tavall.ai.app.config.ChatGPTMcpGatewayProperties;
import org.tavall.cloud.chatgpt.ChatGPTWebMcpCatalog;
import org.tavall.cloud.chatgpt.control.ChatGPTWebControlChannelGuard;
import org.tavall.cloud.chatgpt.control.ChatGPTWebControlGateway;
import org.tavall.cloud.chatgpt.control.ChatGPTWebControlPreflight;
import org.tavall.cloud.chatgpt.function.ChatGPTWebCloudFunctions;
import org.tavall.cloud.chatgpt.function.ChatGPTWebEnvironmentFunctions;
import org.tavall.cloud.command.CloudCommandJsonCodec;
import org.tavall.cloud.command.CloudLocalCommandClient;
import org.tavall.cloud.control.local.CloudLocalControlClient;
import org.tavall.cloud.control.protocol.CloudControlFrameCodec;
import org.tavall.cloud.control.protocol.HmacCloudEnvelopeAuthenticator;
import org.tavall.cloud.control.protocol.JacksonCloudControlEnvelopeCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class ChatGPTCloudCatalogSessionFactory implements McpGatewayCatalogSessionFactory {

  private static final Duration PREFLIGHT_TIMEOUT = Duration.ofSeconds(45);
  private static final Duration PREFLIGHT_RETRY_DELAY = Duration.ofMillis(250);
  private static final String SERVER_NAME = "Tavall Cloud AgentTaskManager MCP";
  private static final String SERVER_VERSION_BASE = "1.1.4-agent-gateway-";
  private final ChatGPTMcpGatewayProperties properties;

  public ChatGPTCloudCatalogSessionFactory(ChatGPTMcpGatewayProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  @Override
  public McpGatewayCatalogSession open() {
    GatewayConfiguration configuration = GatewayConfiguration.from(properties);
    byte[] controlSecret = readControlSecret(configuration.controlSecretPath());
    JacksonCloudControlEnvelopeCodec envelopeCodec = new JacksonCloudControlEnvelopeCodec();
    CloudLocalControlClient controlClient = new CloudLocalControlClient(
        configuration.controlSocketPath(),
        new CloudControlFrameCodec(envelopeCodec, configuration.maximumFrameBytes()),
        new HmacCloudEnvelopeAuthenticator(controlSecret, envelopeCodec),
        CloudLocalControlClient.ReconnectPolicy.FAIL_CLOSED
    );
    ChatGPTWebControlGateway controlGateway = new ChatGPTWebControlGateway(
        configuration.nodeId(),
        new CloudLocalCommandClient(configuration.nodeId(), controlClient, new CloudCommandJsonCodec())
    );
    try {
      ChatGPTWebControlPreflight controlPreflight = new ChatGPTWebControlPreflight(
          configuration.nodeId(),
          controlGateway,
          PREFLIGHT_TIMEOUT,
          PREFLIGHT_RETRY_DELAY
      );
      controlPreflight.connectWithRetry(controlClient::connect);
      ChatGPTWebMcpCatalog catalog = new ChatGPTWebMcpCatalog(
          new ChatGPTWebCloudFunctions(controlGateway),
          new ChatGPTWebEnvironmentFunctions(controlGateway)
      );
      controlPreflight.verifyWithRetry();
      AtomicBoolean closed = new AtomicBoolean();
      AtomicReference<ChatGPTWebControlChannelGuard> controlChannelGuard = new AtomicReference<>();
      return new McpGatewayCatalogSession(
          SERVER_NAME,
          gatewayVersion(catalog),
          catalog.instructions(),
          catalog.toolSpecifications(),
          List.of(catalog.resourceSpecification()),
          onControlChannelLost -> {
            ChatGPTWebControlChannelGuard guard = new ChatGPTWebControlChannelGuard(
                controlClient::isConnected,
                onControlChannelLost
            );
            if (!controlChannelGuard.compareAndSet(null, guard)) {
              throw new IllegalStateException("ChatGPT CONTROL channel guard is already active");
            }
            guard.start();
          },
          () -> {
            if (closed.compareAndSet(false, true)) {
              ChatGPTWebControlChannelGuard guard = controlChannelGuard.getAndSet(null);
              if (guard != null) {
                guard.close();
              }
              controlClient.close();
            }
          }
      );
    } catch (RuntimeException exception) {
      controlClient.close();
      throw exception;
    }
  }

  static String gatewayVersion(ChatGPTWebMcpCatalog catalog) {
    Objects.requireNonNull(catalog, "catalog");
    return SERVER_VERSION_BASE + catalog.toolSpecifications().size() + "+" + catalog.generation();
  }

  private static byte[] readControlSecret(Path controlSecretPath) {
    try {
      byte[] secret = Files.readAllBytes(controlSecretPath);
      if (secret.length < 32) {
        throw new IllegalArgumentException("Tavall Cloud CONTROL secret must contain at least 32 bytes");
      }
      return secret;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to read Tavall Cloud CONTROL secret", exception);
    }
  }

  private record GatewayConfiguration(
      String nodeId,
      Path controlSecretPath,
      Path controlSocketPath,
      int maximumFrameBytes
  ) {

    private static GatewayConfiguration from(ChatGPTMcpGatewayProperties properties) {
      String nodeId = requireText(properties.getControlNodeId(), "controlNodeId");
      if (!nodeId.matches("[a-z0-9][a-z0-9-]{1,62}")) {
        throw new IllegalArgumentException("controlNodeId must be a lowercase DNS-style identifier");
      }
      Path controlSecretPath = requireAbsolutePath(properties.getControlSecretPath(), "controlSecretPath");
      Path controlSocketPath = requireAbsolutePath(properties.getControlSocketPath(), "controlSocketPath");
      int maximumFrameBytes = properties.getMaximumFrameBytes();
      if (maximumFrameBytes < 1024 || maximumFrameBytes > 16 * 1024 * 1024) {
        throw new IllegalArgumentException("maximumFrameBytes is outside the safe range");
      }
      return new GatewayConfiguration(nodeId, controlSecretPath, controlSocketPath, maximumFrameBytes);
    }

    private static Path requireAbsolutePath(Path value, String name) {
      if (value == null || !value.isAbsolute()) {
        throw new IllegalArgumentException(name + " must be an absolute path");
      }
      return value.toAbsolutePath().normalize();
    }

    private static String requireText(String value, String name) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(name + " is required");
      }
      return value.strip();
    }
  }
}
