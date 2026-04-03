package com.agenttaskmanager.app.hytalelearning;

import com.agenttaskmanager.app.bridge.BridgeAutomationService;
import com.agenttaskmanager.app.model.hytalelearning.HytaleLearningSession;
import com.agenttaskmanager.app.model.hytalelearning.HytalePlaybook;
import com.agenttaskmanager.app.model.hytalelearning.HytalePlaybookRequest;
import com.agenttaskmanager.app.persistence.postgres.HytaleLearningSessionRepository;
import com.agenttaskmanager.app.persistence.postgres.HytalePlaybookRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HytalePlaybookService {

  private final BridgeAutomationService bridgeAutomationService;
  private final HytaleLearningSessionRepository learningSessionRepository;
  private final HytalePlaybookRepository playbookRepository;

  public HytalePlaybookService(
      BridgeAutomationService bridgeAutomationService,
      HytaleLearningSessionRepository learningSessionRepository,
      HytalePlaybookRepository playbookRepository
  ) {
    this.bridgeAutomationService = bridgeAutomationService;
    this.learningSessionRepository = learningSessionRepository;
    this.playbookRepository = playbookRepository;
  }

  public HytalePlaybook createPlaybook(HytalePlaybookRequest request) {
    return playbookRepository.create(
        "hpb_" + UUID.randomUUID(),
        request.machineId(),
        request.clientProfileId(),
        request.serverTarget(),
        request.scenarioId(),
        request.name(),
        request.targetWindow(),
        request.actions(),
        request.expectedAnchors(),
        request.failureRecovery(),
        request.latestSummary(),
        request.metadata()
    );
  }

  public List<HytalePlaybook> listPlaybooks(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      boolean executableOnly,
      int limit
  ) {
    return playbookRepository.listByScope(
        machineId,
        clientProfileId,
        serverTarget,
        scenarioId,
        executableOnly,
        limit
    );
  }

  public HytalePlaybook approvePlaybook(String playbookId, String approvedBy, boolean approved) {
    return playbookRepository.updateApproval(playbookId, approved, approvedBy);
  }

  public HytalePlaybook pinPlaybook(String playbookId, String pinnedBy, boolean pinned) {
    return playbookRepository.updatePinned(playbookId, pinned, pinnedBy);
  }

  public HytalePlaybook getPlaybook(String playbookId) {
    return playbookRepository.get(playbookId);
  }

  public List<String> executePlaybook(String learningSessionId, String playbookId, String requestedBy, String requestedFrom) {
    HytaleLearningSession learningSession = learningSessionRepository.get(learningSessionId);
    HytalePlaybook playbook = playbookRepository.get(playbookId);
    if (!playbook.approved() && !playbook.pinned()) {
      throw new IllegalArgumentException("Only approved or pinned Hytale playbooks are executable: " + playbookId);
    }
    if (learningSession.bridgeSessionId() == null || learningSession.bridgeSessionId().isBlank()) {
      throw new IllegalArgumentException("Learning session is not attached to a bridge session: " + learningSessionId);
    }
    ensureScopeMatches(learningSession, playbook);
    List<String> queuedCommandIds = new ArrayList<>();
    for (Map<String, Object> action : playbook.actions()) {
      String commandId = stringValue(action.get("commandId"));
      if (commandId.isBlank()) {
        throw new IllegalArgumentException("Playbook action missing commandId: " + playbookId);
      }
      Map<String, Object> arguments = action.get("arguments") instanceof Map<?, ?> map
          ? (Map<String, Object>) map
          : Map.of();
      queuedCommandIds.add(bridgeAutomationService.queueCommand(
          learningSession.bridgeSessionId(),
          commandId,
          arguments,
          requestedBy,
          requestedFrom
      ).commandRequestId());
    }
    return queuedCommandIds;
  }

  private void ensureScopeMatches(HytaleLearningSession session, HytalePlaybook playbook) {
    requireScope("machine", playbook.machineId(), session.machineId(), playbook.playbookId());
    requireScope("client profile", playbook.clientProfileId(), session.clientProfileId(), playbook.playbookId());
    requireScope("server target", playbook.serverTarget(), session.serverTarget(), playbook.playbookId());
    requireScope("scenario", playbook.scenarioId(), session.scenarioId(), playbook.playbookId());
  }

  private void requireScope(String label, String playbookValue, String sessionValue, String playbookId) {
    if (playbookValue != null && !playbookValue.isBlank() && !playbookValue.equals(sessionValue)) {
      throw new IllegalArgumentException("Hytale playbook " + playbookId + " does not match learning session " + label);
    }
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
