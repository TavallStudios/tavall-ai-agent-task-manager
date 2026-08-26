package org.tavall.ai.app.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryContextAugmentationServiceTest {

  @Test
  void shouldCompileProviderContextAndKeepDegradationVisible() {
    MemoryProviderTelemetryService telemetry = new MemoryProviderTelemetryService();
    MemoryKnowledgeContext graphify = new MemoryKnowledgeContext(
        "graphify",
        MemoryKnowledgeRole.STRUCTURAL,
        "FFAFeatureBootstrap depends on FFARuntimeService.",
        List.of("src/FFAFeatureBootstrap.java:42"),
        Map.of(),
        12L,
        false,
        ""
    );
    MemoryKnowledgeContext graphiti = new MemoryKnowledgeContext(
        "graphiti",
        MemoryKnowledgeRole.TEMPORAL,
        "",
        List.of(),
        Map.of(),
        20L,
        true,
        "temporal store unavailable"
    );
    MemoryContextAugmentationService service = new MemoryContextAugmentationService(
        List.of(new StaticMemoryKnowledgeProvider(graphiti), new StaticMemoryKnowledgeProvider(graphify)),
        telemetry
    );

    MemoryContextAugmentation result = service.augment(
        "tavall-project-novus",
        "/srv/workspace/tavall-project-novus",
        "fix FFA bootstrap regression",
        6,
        Map.of()
    );

    assertEquals(2, result.contexts().size());
    assertTrue(result.section().startsWith("Structural code knowledge"));
    assertTrue(result.section().contains("DEGRADED - temporal store unavailable"));
    assertEquals(1L, telemetry.snapshot().get("graphify").get("calls"));
    assertEquals(1L, telemetry.snapshot().get("graphiti").get("degradedCalls"));
  }
}
