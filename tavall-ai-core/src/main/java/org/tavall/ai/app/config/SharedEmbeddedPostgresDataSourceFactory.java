package org.tavall.ai.app.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import javax.sql.DataSource;

public final class SharedEmbeddedPostgresDataSourceFactory {

  private static final Object MONITOR = new Object();
  private static volatile EmbeddedPostgres embeddedPostgres;
  private static volatile DataSource dataSource;
  private static volatile boolean shutdownHookInstalled;

  private SharedEmbeddedPostgresDataSourceFactory() {
  }

  public static DataSource dataSource() {
    if (dataSource != null) {
      return dataSource;
    }
    synchronized (MONITOR) {
      if (dataSource == null) {
        try {
          embeddedPostgres = EmbeddedPostgres.builder()
              .setPort(0)
              .start();
          dataSource = embeddedPostgres.getPostgresDatabase();
          installShutdownHook();
        } catch (java.io.IOException exception) {
          throw new IllegalStateException("Failed to start the embedded Postgres runtime.", exception);
        }
      }
      return dataSource;
    }
  }

  private static void installShutdownHook() {
    if (shutdownHookInstalled) {
      return;
    }
    Runtime.getRuntime().addShutdownHook(new Thread(SharedEmbeddedPostgresDataSourceFactory::closeQuietly));
    shutdownHookInstalled = true;
  }

  private static void closeQuietly() {
    EmbeddedPostgres current = embeddedPostgres;
    if (current == null) {
      return;
    }
    try {
      current.close();
    } catch (Exception ignored) {
      // Best effort shutdown for the shared embedded Postgres instance.
    }
  }
}

