package com.agenttaskmanager.app.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.agenttaskmanager.app.console.Log;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MongoConnectionStringResolver {

  private static final String DEFAULT_LOCAL_URI = "mongodb://127.0.0.1:27017";

  public String resolve(MongoProperties properties) {
    String configuredUri = properties.getUri();
    if (configuredUri == null || configuredUri.isBlank()) {
      return configuredUri;
    }
    if (!DEFAULT_LOCAL_URI.equals(configuredUri)) {
      return configuredUri;
    }
    String discoveredUri = discoverDockerBackedUri(properties.getDatabase());
    if (discoveredUri != null) {
      return discoveredUri;
    }
    return configuredUri;
  }

  private String discoverDockerBackedUri(String database) {
    ProcessBuilder processBuilder = new ProcessBuilder(
        "docker",
        "inspect",
        "mcp-mongodb-live",
        "--format",
        "{{range .Config.Env}}{{println .}}{{end}}"
    );

    try {
      Process process = processBuilder.start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        Map<String, String> envValues = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
          int separatorIndex = line.indexOf('=');
          if (separatorIndex > 0) {
            envValues.put(line.substring(0, separatorIndex), line.substring(separatorIndex + 1));
          }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
          return null;
        }
        String username = envValues.get("MONGO_INITDB_ROOT_USERNAME");
        String password = envValues.get("MONGO_INITDB_ROOT_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
          return null;
        }
        return "mongodb://%s:%s@127.0.0.1:27017/%s?authSource=admin".formatted(
            encode(username),
            encode(password),
            database
        );
      }
    } catch (Exception exception) {
      Log.debug("Falling back to configured Mongo URI because docker inspection failed: {}", exception.getMessage());
      return null;
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
