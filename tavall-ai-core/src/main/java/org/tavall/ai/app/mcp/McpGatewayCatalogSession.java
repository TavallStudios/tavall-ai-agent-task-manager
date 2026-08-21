package org.tavall.ai.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A verified, transport-independent MCP catalog bound to one gateway connection. */
public final class McpGatewayCatalogSession implements AutoCloseable {

  private final Consumer<Runnable> activation;
  private final AtomicBoolean activated = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Runnable closeAction;
  private final List<SyncResourceSpecification> resourceSpecifications;
  private final String serverName;
  private final String serverVersion;
  private final String instructions;
  private final List<SyncToolSpecification> toolSpecifications;

  public McpGatewayCatalogSession(
      String serverName,
      String serverVersion,
      String instructions,
      List<SyncToolSpecification> toolSpecifications,
      List<SyncResourceSpecification> resourceSpecifications,
      Consumer<Runnable> activation,
      Runnable closeAction
  ) {
    this.serverName = requireText(serverName, "serverName");
    this.serverVersion = requireText(serverVersion, "serverVersion");
    this.instructions = requireText(instructions, "instructions");
    this.toolSpecifications = List.copyOf(Objects.requireNonNull(toolSpecifications, "toolSpecifications"));
    this.resourceSpecifications = List.copyOf(Objects.requireNonNull(resourceSpecifications, "resourceSpecifications"));
    this.activation = Objects.requireNonNull(activation, "activation");
    this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
  }

  public void activate(Runnable onControlChannelLost) {
    if (closed.get()) {
      throw new IllegalStateException("Gateway catalog session is already closed");
    }
    if (!activated.compareAndSet(false, true)) {
      throw new IllegalStateException("Gateway catalog session is already active");
    }
    activation.accept(Objects.requireNonNull(onControlChannelLost, "onControlChannelLost"));
  }

  public String instructions() {
    return instructions;
  }

  public List<SyncResourceSpecification> resourceSpecifications() {
    return resourceSpecifications;
  }

  public String serverName() {
    return serverName;
  }

  public String serverVersion() {
    return serverVersion;
  }

  public List<SyncToolSpecification> toolSpecifications() {
    return toolSpecifications;
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      closeAction.run();
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
