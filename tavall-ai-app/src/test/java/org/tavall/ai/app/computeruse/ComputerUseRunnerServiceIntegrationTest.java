package org.tavall.ai.app.computeruse;

import org.tavall.ai.app.model.computeruse.ComputerUseCaptureRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseCaptureResult;
import org.tavall.ai.app.model.computeruse.ComputerUseInputBatch;
import org.tavall.ai.app.model.computeruse.ComputerUseProcessLaunchRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseRunnerRegistration;
import org.tavall.ai.app.model.computeruse.ComputerUseRunnerSummary;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionSummary;
import org.tavall.ai.app.model.computeruse.ComputerUseVisionMatch;
import org.tavall.ai.app.model.computeruse.ComputerUseVisionWaitRequest;
import org.tavall.ai.app.persistence.postgres.ComputerUseArtifactRepository;
import org.tavall.ai.app.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerUseRunnerServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private ComputerUseRunnerService runnerService;

  @Autowired
  private ComputerUseArtifactRepository artifactRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void shouldRunComputerUseSessionFlowAgainstExternalRunner() throws Exception {
    AtomicInteger templateAttempts = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/api/automation/capabilities", exchange -> {
      String body = objectMapper.writeValueAsString(Map.of(
          "ok", true,
          "result", Map.of(
              "endpoints", Map.of(
                  "command", "/api/automation/command"
              )
          )
      ));
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream outputStream = exchange.getResponseBody()) {
        outputStream.write(body.getBytes(StandardCharsets.UTF_8));
      }
    });
    server.createContext("/api/automation/command", exchange -> {
      Map<?, ?> request = objectMapper.readValue(exchange.getRequestBody(), Map.class);
      String command = String.valueOf(request.get("command"));
      String body = responseFor(command, templateAttempts.incrementAndGet());
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream outputStream = exchange.getResponseBody()) {
        outputStream.write(body.getBytes(StandardCharsets.UTF_8));
      }
    });
    server.start();

    ComputerUseRunnerSummary runner = runnerService.registerRunner(new ComputerUseRunnerRegistration(
        "runner-1",
        "Runner One",
        "test-host",
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "C:/HytaleLauncher.exe",
        "C:/HytaleClient.exe",
        List.of("graphics-capture", "copyFromScreen"),
        Map.of("gameSupport", true),
        Map.of("surface", "external-runner")
    ));

    ComputerUseSessionSummary session = runnerService.startSession(new ComputerUseSessionRequest(
        runner.runnerId(),
        null,
        null,
        "hytale/gameplay-assets-visible",
        null,
        null,
        List.of(),
        List.of(),
        Map.of(),
        Map.of("testCase", "integration")
    ));

    assertEquals("RUNNING", session.status());
    assertEquals("Remote Dev", session.serverTarget());
    assertEquals("debug/test-4k", session.chartId());
    assertEquals("hytale/gameplay-assets-visible", session.scenarioId());
    assertTrue(session.metadata().containsKey("scenarioDefinition"));
    assertTrue(session.metadata().containsKey("visualAnchors"));
    assertTrue(session.metadata().containsKey("gameplayKeybinds"));
    @SuppressWarnings("unchecked")
    Map<String, Object> scenarioDefinition = (Map<String, Object>) session.metadata().get("scenarioDefinition");
    assertEquals("hytale/gameplay-assets-visible", scenarioDefinition.get("scenarioId"));
    assertFalse(((List<?>) scenarioDefinition.get("steps")).isEmpty());

    runnerService.launchProcess(new ComputerUseProcessLaunchRequest(
        session.sessionId(),
        "launcher",
        null,
        "",
        "",
        1000,
        ""
    ));

    ComputerUseCaptureResult capture = runnerService.captureWindow(new ComputerUseCaptureRequest(
        session.sessionId(),
        "Hytale",
        "Hytale",
        true,
        "gameplay-assets",
        "Gameplay frame"
    ));

    assertNotNull(capture.artifact());
    assertFalse(artifactRepository.listArtifacts(session.sessionId()).isEmpty());

    runnerService.sendInput(
        session.sessionId(),
        new ComputerUseInputBatch(
            true,
            List.of(new ComputerUseInputBatch.KeyboardAction("Space", null, null, "press", 0, false)),
            List.of(new ComputerUseInputBatch.MouseAction("click", 100, 120, "left", 0, 0, "client"))
        )
    );

    ComputerUseVisionMatch match = runnerService.waitForVisionMatch(new ComputerUseVisionWaitRequest(
        session.sessionId(),
        "gameplayAssets",
        "C:/anchors/gameplay.png",
        "Hytale",
        "Hytale",
        0.90d,
        1000,
        true
    ));

    assertTrue(match.matched());
    assertNotNull(match.artifact());

    ComputerUseSessionSummary stopped = runnerService.stopSession(session.sessionId());
    assertEquals("STOPPED", stopped.status());
  }

  @Test
  void shouldFallbackToCompatibilityRunnerCommandPathWhenCanonicalEndpointIsMissing() throws Exception {
    AtomicInteger templateAttempts = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/request", exchange -> {
      Map<?, ?> request = objectMapper.readValue(exchange.getRequestBody(), Map.class);
      String command = String.valueOf(request.get("command"));
      String body = responseFor(command, templateAttempts.incrementAndGet());
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream outputStream = exchange.getResponseBody()) {
        outputStream.write(body.getBytes(StandardCharsets.UTF_8));
      }
    });
    server.start();

    ComputerUseRunnerSummary runner = runnerService.registerRunner(new ComputerUseRunnerRegistration(
        "runner-compat",
        "Runner Compat",
        "compat-host",
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "C:/HytaleLauncher.exe",
        "C:/HytaleClient.exe",
        List.of("graphics-capture"),
        Map.of("gameSupport", true),
        Map.of("surface", "compat")
    ));

    ComputerUseSessionSummary session = runnerService.startSession(new ComputerUseSessionRequest(
        runner.runnerId(),
        null,
        null,
        "hytale/launch-and-join-smoke",
        null,
        null,
        List.of(),
        List.of(),
        Map.of(),
        Map.of("testCase", "compatibility")
    ));

    assertEquals("RUNNING", session.status());
  }

  private String responseFor(String command, int attempt) throws IOException {
    String png = Base64.getEncoder().encodeToString("png".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> result = switch (command) {
      case "ping" -> Map.of("status", "ok");
      case "launch_process" -> Map.of(
          "id", 501,
          "processName", "HytaleLauncher",
          "window", Map.of("handleHex", "0x1", "processName", "HytaleLauncher")
      );
      case "capture_stream_frame" -> Map.of(
          "captureMode", "copyFromScreen",
          "outputPath", "runner://capture.png",
          "base64Png", png
      );
      case "send_key_batch" -> Map.of("keyboardActionCount", 1, "intrusive", true);
      case "send_mouse_batch" -> Map.of("mouseActionCount", 1, "intrusive", true);
      case "match_template" -> Map.of(
          "matched", attempt > 1,
          "score", attempt > 1 ? 0.98d : 0.40d,
          "bounds", Map.of("x", 10, "y", 20, "width", 30, "height", 40),
          "base64Png", png
      );
      default -> Map.of();
    };
    return objectMapper.writeValueAsString(Map.of("id", command, "ok", true, "result", result));
  }
}

