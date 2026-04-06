package org.tavall.ai.app.hytalelearning;

import org.tavall.ai.app.model.hytalelearning.HytaleLearningSession;
import org.tavall.ai.app.model.hytalelearning.HytaleTimelineFrame;
import org.tavall.ai.app.model.hytalelearning.HytaleTimelineFrameRequest;
import org.tavall.ai.app.model.hytalelearning.HytaleVisualAnchor;
import org.tavall.ai.app.model.hytalelearning.HytaleVisualAnchorRequest;
import org.tavall.ai.app.persistence.mongo.ArtifactDocumentStore;
import org.tavall.ai.app.persistence.postgres.HytaleTimelineFrameRepository;
import org.tavall.ai.app.persistence.postgres.HytaleVisualAnchorRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HytaleLearningArtifactService {

  private static final String MONGO_BACKEND = "mongo";

  private final ArtifactDocumentStore artifactDocumentStore;
  private final HytaleTimelineFrameRepository timelineFrameRepository;
  private final HytaleVisualAnchorRepository visualAnchorRepository;

  public HytaleLearningArtifactService(
      ArtifactDocumentStore artifactDocumentStore,
      HytaleTimelineFrameRepository timelineFrameRepository,
      HytaleVisualAnchorRepository visualAnchorRepository
  ) {
    this.artifactDocumentStore = artifactDocumentStore;
    this.timelineFrameRepository = timelineFrameRepository;
    this.visualAnchorRepository = visualAnchorRepository;
  }

  public HytaleTimelineFrame storeTimelineFrame(
      HytaleLearningSession session,
      HytaleTimelineFrameRequest request
  ) {
    String frameId = "htf_" + UUID.randomUUID();
    Map<String, Object> metadata = new LinkedHashMap<>(request.metadata() == null ? Map.of() : request.metadata());
    metadata.put("sessionId", session.sessionId());
    metadata.put("artifactType", "timeline-frame");
    artifactDocumentStore.storeLearningArtifactBody(
        frameId,
        request.artifactKind(),
        request.base64Body() == null ? "" : request.base64Body(),
        metadata
    );
    return timelineFrameRepository.create(
        frameId,
        session.sessionId(),
        request.sourceWindow(),
        request.artifactKind(),
        MONGO_BACKEND,
        frameId,
        request.summary(),
        metadata
    );
  }

  public HytaleVisualAnchor storeVisualAnchor(HytaleVisualAnchorRequest request) {
    String anchorId = stableAnchorId(request);
    Map<String, Object> metadata = new LinkedHashMap<>(request.metadata() == null ? Map.of() : request.metadata());
    metadata.put("artifactType", "anchor-capture");
    if (request.captureBase64() != null && !request.captureBase64().isBlank()) {
      artifactDocumentStore.storeLearningArtifactBody(
          anchorId,
          "anchor-capture",
          request.captureBase64(),
          metadata
      );
    }
    return visualAnchorRepository.upsert(
        anchorId,
        request.machineId(),
        request.clientProfileId(),
        request.serverTarget(),
        request.scenarioId(),
        request.anchorKey(),
        request.sourceWindow(),
        request.normalizedRegion(),
        request.description(),
        request.confidence(),
        MONGO_BACKEND,
        request.captureBase64() == null || request.captureBase64().isBlank() ? null : anchorId,
        metadata
    );
  }

  private String stableAnchorId(HytaleVisualAnchorRequest request) {
    String raw = String.join(
        "|",
        value(request.machineId()),
        value(request.clientProfileId()),
        value(request.serverTarget()),
        value(request.scenarioId()),
        value(request.anchorKey()),
        value(request.sourceWindow())
    );
    return "ha_" + UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}

