package org.tavall.ai.app.mcp;

import org.tavall.ai.app.config.ChatGPTMcpGatewayProperties;
import org.tavall.ai.app.console.Log;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class ChatGPTMcpUnixSocketGateway implements SmartLifecycle, AutoCloseable {

  private final Set<McpGatewayConnection> connections = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean running = new AtomicBoolean();
  private final ChatGPTMcpGatewayProperties properties;
  private final McpGatewayCatalogSessionFactory catalogSessionFactory;
  private final McpJsonMapper jsonMapper;
  private volatile ServerSocketChannel serverSocketChannel;
  private volatile Path socketPath;

  public ChatGPTMcpUnixSocketGateway(
      ChatGPTMcpGatewayProperties properties,
      McpGatewayCatalogSessionFactory catalogSessionFactory,
      @Qualifier("mcpJsonMapper") McpJsonMapper jsonMapper
  ) {
    this.properties = properties;
    this.catalogSessionFactory = catalogSessionFactory;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void start() {
    if (!properties.isEnabled() || !running.compareAndSet(false, true)) {
      return;
    }
    try {
      socketPath = requireSocketPath(properties.getSocketPath());
      removeStaleSocket(socketPath);
      ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      channel.bind(UnixDomainSocketAddress.of(socketPath));
      // Store the channel before applying filesystem permissions so startup rollback closes it.
      serverSocketChannel = channel;
      McpGatewaySocketPermissions.apply(socketPath, properties.getSocketGroup());
      Thread.ofVirtual().name("agent-task-manager-chatgpt-mcp-accept").start(this::acceptConnections);
      Log.info("ChatGPT MCP gateway listening on {}", socketPath);
    } catch (IOException | RuntimeException exception) {
      close();
      throw new IllegalStateException("Unable to start the ChatGPT MCP Unix socket gateway", exception);
    }
  }

  @Override
  public void stop() {
    close();
  }

  @Override
  public void stop(Runnable callback) {
    close();
    callback.run();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }

  @Override
  public void close() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    closeServerSocket();
    connections.forEach(McpGatewayConnection::close);
    removeSocketOnClose();
  }

  private void acceptConnections() {
    while (running.get()) {
      try {
        SocketChannel client = serverSocketChannel.accept();
        connections.forEach(McpGatewayConnection::close);
        Thread.ofVirtual().name("agent-task-manager-chatgpt-mcp-session").start(() -> serve(client));
      } catch (IOException exception) {
        if (running.get()) {
          Log.warn("ChatGPT MCP gateway stopped accepting connections: {}", exception.getMessage());
          close();
        }
        return;
      }
    }
  }

  private void serve(SocketChannel client) {
    McpGatewayCatalogSession catalogSession = null;
    try {
      CloseTrackingInputStream input = new CloseTrackingInputStream(Channels.newInputStream(client));
      OutputStream output = Channels.newOutputStream(client);
      catalogSession = catalogSessionFactory.open();
      StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper, input, output);
      McpSyncServer server = McpServer.sync(transportProvider)
          .serverInfo(catalogSession.serverName(), catalogSession.serverVersion())
          .instructions(catalogSession.instructions())
          .capabilities(McpSchema.ServerCapabilities.builder().tools(true).resources(false, false).build())
          .jsonMapper(jsonMapper)
          .tools(catalogSession.toolSpecifications())
          .resources(catalogSession.resourceSpecifications())
          .build();
      AtomicReference<McpGatewayConnection> connectionReference = new AtomicReference<>();
      McpGatewayConnection connection = new McpGatewayConnection(
          client,
          catalogSession,
          transportProvider,
          server,
          () -> connections.remove(connectionReference.get())
      );
      connectionReference.set(connection);
      connections.add(connection);
      input.onClosed(connection::close);
      catalogSession.activate(connection::close);
    } catch (RuntimeException exception) {
      if (catalogSession != null) {
        catalogSession.close();
      }
      closeClient(client);
      Log.warn("Rejected ChatGPT MCP gateway connection: {}", exception.getMessage());
    }
  }

  private static Path requireSocketPath(Path candidate) {
    if (candidate == null || !candidate.isAbsolute()) {
      throw new IllegalArgumentException("socketPath must be an absolute path");
    }
    Path normalized = candidate.toAbsolutePath().normalize();
    Path parent = normalized.getParent();
    if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("socketPath parent must be an existing directory");
    }
    return normalized;
  }

  private static void removeStaleSocket(Path socketPath) throws IOException {
    if (!Files.exists(socketPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    BasicFileAttributes attributes = Files.readAttributes(
        socketPath,
        BasicFileAttributes.class,
        LinkOption.NOFOLLOW_LINKS
    );
    if (Files.isSymbolicLink(socketPath) || !attributes.isOther()) {
      throw new IllegalStateException("Refusing to replace a non-socket gateway path");
    }
    Files.delete(socketPath);
  }

  private void closeServerSocket() {
    ServerSocketChannel channel = serverSocketChannel;
    if (channel == null) {
      return;
    }
    try {
      channel.close();
    } catch (IOException ignored) {
    }
  }

  private void removeSocketOnClose() {
    Path path = socketPath;
    if (path == null) {
      return;
    }
    try {
      removeStaleSocket(path);
    } catch (IOException | RuntimeException exception) {
      Log.warn("Unable to remove ChatGPT MCP gateway socket: {}", exception.getMessage());
    }
  }

  private static void closeClient(SocketChannel client) {
    try {
      client.close();
    } catch (IOException ignored) {
    }
  }

  private static final class CloseTrackingInputStream extends FilterInputStream {
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Runnable onClosed = () -> {
    };

    private CloseTrackingInputStream(InputStream input) {
      super(input);
    }

    private void onClosed(Runnable action) {
      onClosed = action;
    }

    @Override
    public int read() throws IOException {
      try {
        return track(super.read());
      } catch (IOException exception) {
        notifyClosed();
        throw exception;
      }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      try {
        return track(super.read(buffer, offset, length));
      } catch (IOException exception) {
        notifyClosed();
        throw exception;
      }
    }

    @Override
    public void close() throws IOException {
      try {
        super.close();
      } finally {
        notifyClosed();
      }
    }

    private int track(int read) {
      if (read < 0) {
        notifyClosed();
      }
      return read;
    }

    private void notifyClosed() {
      if (closed.compareAndSet(false, true)) {
        onClosed.run();
      }
    }
  }
}
