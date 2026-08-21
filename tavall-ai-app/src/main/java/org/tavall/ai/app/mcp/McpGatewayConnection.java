package org.tavall.ai.app.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class McpGatewayConnection implements AutoCloseable {

  private final AtomicBoolean closed = new AtomicBoolean();
  private final Runnable onClosed;
  private final McpGatewayCatalogSession catalogSession;
  private final SocketChannel socketChannel;
  private final McpSyncServer server;
  private final StdioServerTransportProvider transportProvider;

  McpGatewayConnection(
      SocketChannel socketChannel,
      McpGatewayCatalogSession catalogSession,
      StdioServerTransportProvider transportProvider,
      McpSyncServer server,
      Runnable onClosed
  ) {
    this.socketChannel = Objects.requireNonNull(socketChannel, "socketChannel");
    this.catalogSession = Objects.requireNonNull(catalogSession, "catalogSession");
    this.transportProvider = Objects.requireNonNull(transportProvider, "transportProvider");
    this.server = Objects.requireNonNull(server, "server");
    this.onClosed = Objects.requireNonNull(onClosed, "onClosed");
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      server.close();
    } finally {
      try {
        transportProvider.closeGracefully().subscribe();
      } finally {
        try {
          catalogSession.close();
        } finally {
          closeSocket();
          onClosed.run();
        }
      }
    }
  }

  private void closeSocket() {
    try {
      socketChannel.close();
    } catch (IOException ignored) {
    }
  }
}
