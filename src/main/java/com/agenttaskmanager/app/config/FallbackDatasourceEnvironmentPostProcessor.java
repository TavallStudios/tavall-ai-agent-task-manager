package com.agenttaskmanager.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class FallbackDatasourceEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  private static final Pattern EXPORT_PATTERN =
      Pattern.compile("^(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$");

  private static final List<Path> ENV_FILES = List.of(
      Path.of("/etc/tavallcouriers.env"),
      Path.of("/etc/tavall/tavall.env")
  );

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment,
      SpringApplication application
  ) {
    if (StringUtils.hasText(environment.getProperty("spring.datasource.url"))) {
      return;
    }

    Map<String, String> values = resolveDatasourceValues();
    if (!values.isEmpty()) {
      Map<String, Object> propertySource = new LinkedHashMap<>();
      putIfPresent(propertySource, "spring.datasource.url", normalizeJdbc(values.get("url")));
      putIfPresent(propertySource, "spring.datasource.username", values.get("username"));
      putIfPresent(propertySource, "spring.datasource.password", values.get("password"));
      environment.getPropertySources().addFirst(
          new MapPropertySource("agentTaskManagerFallbackDatasource", propertySource)
      );
    }
  }

  private static void putIfPresent(Map<String, Object> target, String key, String value) {
    if (StringUtils.hasText(value)) {
      target.put(key, value);
    }
  }

  private Map<String, String> resolveDatasourceValues() {
    for (Path envFile : ENV_FILES) {
      Map<String, String> parsed = parseEnvFile(readEnvFile(envFile));
      String jdbcUrl = firstNonBlank(parsed.get("NOVUS_POSTGRES_URL"), parsed.get("DB_URL"));
      if (!StringUtils.hasText(jdbcUrl)) {
        continue;
      }
      Map<String, String> values = new LinkedHashMap<>();
      values.put("url", jdbcUrl);
      values.put("username", firstNonBlank(parsed.get("NOVUS_POSTGRES_USER"), parsed.get("DB_USER")));
      values.put("password", firstNonBlank(parsed.get("NOVUS_POSTGRES_PASSWORD"), parsed.get("DB_PASS")));
      return values;
    }
    return Map.of();
  }

  private static String normalizeJdbc(String rawUrl) {
    if (rawUrl == null) {
      return null;
    }
    return rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl;
  }

  private static String firstNonBlank(String first, String second) {
    if (StringUtils.hasText(first)) {
      return first;
    }
    if (StringUtils.hasText(second)) {
      return second;
    }
    return null;
  }

  private static String readEnvFile(Path envFile) {
    if (Files.isReadable(envFile)) {
      try {
        return Files.readString(envFile, StandardCharsets.UTF_8);
      } catch (IOException ignored) {
        return "";
      }
    }

    ProcessBuilder processBuilder = new ProcessBuilder("bash", "-lc", "sudo -n cat " + envFile);
    processBuilder.redirectErrorStream(true);
    try {
      Process process = processBuilder.start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      return exitCode == 0 ? output : "";
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
      return "";
    } catch (IOException ignored) {
      return "";
    }
  }

  private static Map<String, String> parseEnvFile(String content) {
    if (!StringUtils.hasText(content)) {
      return Map.of();
    }

    Map<String, String> parsed = new LinkedHashMap<>();
    for (String line : content.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      Matcher matcher = EXPORT_PATTERN.matcher(trimmed);
      if (!matcher.matches()) {
        continue;
      }
      parsed.put(matcher.group(1), stripQuotes(matcher.group(2).trim()));
    }
    return parsed;
  }

  private static String stripQuotes(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
