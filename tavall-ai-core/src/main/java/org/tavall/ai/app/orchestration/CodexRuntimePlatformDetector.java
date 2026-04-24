package org.tavall.ai.app.orchestration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class CodexRuntimePlatformDetector {

  private final Supplier<Map<String, String>> environmentSupplier;
  private final Supplier<String> osNameSupplier;
  private final Supplier<String> procVersionSupplier;

  public CodexRuntimePlatformDetector() {
    this(() -> System.getProperty("os.name", ""), System::getenv, CodexRuntimePlatformDetector::readProcVersion);
  }

  CodexRuntimePlatformDetector(
      Supplier<String> osNameSupplier,
      Supplier<Map<String, String>> environmentSupplier,
      Supplier<String> procVersionSupplier
  ) {
    this.osNameSupplier = osNameSupplier;
    this.environmentSupplier = environmentSupplier;
    this.procVersionSupplier = procVersionSupplier;
  }

  public CodexRuntimePlatform detectCurrentPlatform() {
    return classify(osNameSupplier.get(), environmentSupplier.get(), procVersionSupplier.get());
  }

  static CodexRuntimePlatform classify(String osName, Map<String, String> environment, String procVersion) {
    String normalizedOsName = normalize(osName);
    if (normalizedOsName.contains("win")) {
      return CodexRuntimePlatform.WINDOWS_NATIVE;
    }
    if (normalizedOsName.contains("linux") && looksLikeWsl(environment, procVersion)) {
      return CodexRuntimePlatform.WINDOWS_WSL;
    }
    return CodexRuntimePlatform.NON_WINDOWS;
  }

  private static boolean looksLikeWsl(Map<String, String> environment, String procVersion) {
    Map<String, String> safeEnvironment = environment == null ? Map.of() : environment;
    if (safeEnvironment.containsKey("WSL_DISTRO_NAME") || safeEnvironment.containsKey("WSL_INTEROP")) {
      return true;
    }
    String normalizedProcVersion = normalize(procVersion);
    return normalizedProcVersion.contains("microsoft");
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
  }

  private static String readProcVersion() {
    Path procVersion = Path.of("/proc/version");
    if (!Files.isRegularFile(procVersion)) {
      return "";
    }
    try {
      return Files.readString(procVersion, StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      return "";
    }
  }
}

