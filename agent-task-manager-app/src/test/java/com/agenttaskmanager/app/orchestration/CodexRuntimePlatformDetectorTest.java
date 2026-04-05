package com.agenttaskmanager.app.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CodexRuntimePlatformDetectorTest {

  @Test
  void shouldClassifyNativeWindows() {
    CodexRuntimePlatformDetector detector = new CodexRuntimePlatformDetector(
        () -> "Windows 11",
        () -> Map.of(),
        () -> ""
    );

    assertEquals(CodexRuntimePlatform.WINDOWS_NATIVE, detector.detectCurrentPlatform());
  }

  @Test
  void shouldClassifyWslFromEnvironment() {
    CodexRuntimePlatformDetector detector = new CodexRuntimePlatformDetector(
        () -> "Linux",
        () -> Map.of("WSL_DISTRO_NAME", "Ubuntu"),
        () -> ""
    );

    assertEquals(CodexRuntimePlatform.WINDOWS_WSL, detector.detectCurrentPlatform());
  }

  @Test
  void shouldClassifyNonWindowsWhenWslMarkersAreMissing() {
    CodexRuntimePlatformDetector detector = new CodexRuntimePlatformDetector(
        () -> "Linux",
        () -> Map.of(),
        () -> "Linux version 6.8.0"
    );

    assertEquals(CodexRuntimePlatform.NON_WINDOWS, detector.detectCurrentPlatform());
  }
}
