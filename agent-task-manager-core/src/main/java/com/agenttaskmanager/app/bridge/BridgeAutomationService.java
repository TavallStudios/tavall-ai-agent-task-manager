package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.model.BridgeSessionNotFoundException;
import com.agenttaskmanager.app.model.bridge.BridgeAutomationClaim;
import com.agenttaskmanager.app.model.bridge.BridgeAutomationCommandDefinition;
import com.agenttaskmanager.app.model.bridge.BridgeAutomationCommandSummary;
import com.agenttaskmanager.app.model.bridge.BridgeAutomationSessionCapabilities;
import com.agenttaskmanager.app.model.bridge.BridgeSessionRecord;
import com.agenttaskmanager.app.persistence.postgres.BridgeAutomationCommandRepository;
import com.agenttaskmanager.app.persistence.postgres.BridgeSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BridgeAutomationService {

  private final BridgeAutomationCommandCatalog commandCatalog;
  private final BridgeAutomationCommandRepository commandRepository;
  private final BridgeSessionRepository bridgeSessionRepository;

  public BridgeAutomationService(
      BridgeAutomationCommandCatalog commandCatalog,
      BridgeAutomationCommandRepository commandRepository,
      BridgeSessionRepository bridgeSessionRepository
  ) {
    this.commandCatalog = commandCatalog;
    this.commandRepository = commandRepository;
    this.bridgeSessionRepository = bridgeSessionRepository;
  }

  public List<BridgeAutomationCommandDefinition> listDefinitions() {
    return commandCatalog.listDefinitions();
  }

  public BridgeAutomationSessionCapabilities getSessionCapabilities(String sessionId) {
    BridgeSessionRecord session = requireSession(sessionId);
    return new BridgeAutomationSessionCapabilities(
        session.sessionId(),
        session.agentId(),
        session.repoPath(),
        session.bridgeTarget(),
        session.transport(),
        cooperativeAutomation(session),
        intrusiveInput(session),
        supportedCommands(session)
    );
  }

  public List<BridgeAutomationCommandSummary> listSessionCommands(String sessionId, int limit) {
    requireSession(sessionId);
    return commandRepository.listForSession(sessionId, limit);
  }

  public BridgeAutomationCommandSummary queueCommand(
      String sessionId,
      String commandId,
      Map<String, Object> arguments,
      String requestedBy,
      String requestedFrom
  ) {
    BridgeSessionRecord session = requireSession(sessionId);
    BridgeAutomationCommandDefinition definition = commandCatalog.requireDefinition(commandId);
    if (!cooperativeAutomation(session)) {
      throw new IllegalArgumentException("Bridge session does not advertise cooperative automation support: " + sessionId);
    }
    if (intrusiveInput(session)) {
      throw new IllegalArgumentException("Bridge session allows intrusive input and is not safe for non-focus automation: " + sessionId);
    }
    if (!supportedCommands(session).contains(commandId)) {
      throw new IllegalArgumentException("Bridge session does not support automation command " + commandId + ": " + sessionId);
    }
    return commandRepository.enqueue(
        "ac_" + UUID.randomUUID(),
        session.sessionId(),
        session.agentId(),
        session.repoPath(),
        session.bridgeTarget(),
        definition.commandId(),
        definition.isolationClass(),
        arguments == null ? Map.of() : arguments,
        requestedBy,
        requestedFrom
    );
  }

  public Optional<BridgeAutomationClaim> claimNextCommand(String sessionId, String agentId) {
    BridgeSessionRecord session = requireSession(sessionId);
    if (!session.agentId().equals(agentId)) {
      throw new IllegalArgumentException("Bridge agent mismatch for session " + sessionId + ": " + agentId);
    }
    return commandRepository.claimNextQueued(sessionId);
  }

  public BridgeAutomationCommandSummary completeCommand(
      String commandRequestId,
      String summary,
      Map<String, Object> result
  ) {
    return commandRepository.complete(commandRequestId, summary, result == null ? Map.of() : result);
  }

  public BridgeAutomationCommandSummary failCommand(
      String commandRequestId,
      String summary,
      Map<String, Object> result
  ) {
    return commandRepository.fail(commandRequestId, summary, result == null ? Map.of() : result);
  }

  private BridgeSessionRecord requireSession(String sessionId) {
    return bridgeSessionRepository.findBridgeSession(sessionId)
        .orElseThrow(() -> new BridgeSessionNotFoundException(sessionId));
  }

  private boolean cooperativeAutomation(BridgeSessionRecord session) {
    return Boolean.TRUE.equals(session.capabilities().get("cooperativeAutomation"));
  }

  private boolean intrusiveInput(BridgeSessionRecord session) {
    return Boolean.TRUE.equals(session.capabilities().get("intrusiveInput"));
  }

  private List<String> supportedCommands(BridgeSessionRecord session) {
    Object commands = session.capabilities().get("automationCommands");
    if (commands instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }
}
