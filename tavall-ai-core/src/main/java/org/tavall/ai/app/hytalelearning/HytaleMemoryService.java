package org.tavall.ai.app.hytalelearning;

import org.tavall.ai.app.model.hytalelearning.HytaleLearningSession;
import org.tavall.ai.app.model.hytalelearning.HytaleMemoryQuery;
import org.tavall.ai.app.model.hytalelearning.HytalePlaybook;
import org.tavall.ai.app.model.hytalelearning.HytalePromotionDecision;
import org.tavall.ai.app.model.hytalelearning.HytalePromotionRequest;
import org.tavall.ai.app.model.hytalelearning.HytaleRetrievedMemory;
import org.tavall.ai.app.model.hytalelearning.HytaleVisualAnchor;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.persistence.postgres.HytaleLearningSessionRepository;
import org.tavall.ai.app.persistence.postgres.HytalePlaybookRepository;
import org.tavall.ai.app.persistence.postgres.HytalePromotionDecisionRepository;
import org.tavall.ai.app.persistence.postgres.HytaleVisualAnchorRepository;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.retrieval.SemanticMemoryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HytaleMemoryService {

  private final HytaleLearningProjectKeyFactory projectKeyFactory;
  private final HytaleLearningSessionRepository learningSessionRepository;
  private final HytalePlaybookRepository playbookRepository;
  private final HytalePromotionDecisionRepository promotionDecisionRepository;
  private final HytaleVisualAnchorRepository visualAnchorRepository;
  private final SemanticMemoryService semanticMemoryService;

  public HytaleMemoryService(
      HytaleLearningProjectKeyFactory projectKeyFactory,
      HytaleLearningSessionRepository learningSessionRepository,
      HytalePlaybookRepository playbookRepository,
      HytalePromotionDecisionRepository promotionDecisionRepository,
      HytaleVisualAnchorRepository visualAnchorRepository,
      SemanticMemoryService semanticMemoryService
  ) {
    this.projectKeyFactory = projectKeyFactory;
    this.learningSessionRepository = learningSessionRepository;
    this.playbookRepository = playbookRepository;
    this.promotionDecisionRepository = promotionDecisionRepository;
    this.visualAnchorRepository = visualAnchorRepository;
    this.semanticMemoryService = semanticMemoryService;
  }

  public HytalePromotionDecision promote(HytalePromotionRequest request) {
    HytaleLearningSession session = request.sessionId() == null || request.sessionId().isBlank()
        ? null
        : learningSessionRepository.get(request.sessionId());
    PromotionEvaluation evaluation = evaluatePromotion(request, session);
    Map<String, Object> payload = new LinkedHashMap<>(evaluation.scopePayload());
    payload.put("subjectType", request.subjectType());
    payload.put("subjectId", request.subjectId());
    payload.put("semanticKind", request.semanticKind());
    payload.put("decisionStatus", evaluation.decisionStatus());
    payload.putAll(request.metadata() == null ? Map.of() : request.metadata());
    List<String> pointIds = evaluation.promotable()
        ? semanticMemoryService.storeProjectDocument(
            evaluation.projectKey(),
            session == null ? null : session.sessionId(),
            null,
            request.semanticKind(),
            request.summary(),
            request.body(),
            SemanticCollectionDomain.TASK_HISTORY,
            contentType(request.semanticKind()),
            payload
        )
        : List.of();
    return promotionDecisionRepository.create(
        "hpd_" + UUID.randomUUID(),
        session == null ? null : session.sessionId(),
        request.subjectType(),
        request.subjectId(),
        request.semanticKind(),
        evaluation.decisionStatus(),
        request.summary(),
        pointIds.isEmpty() ? null : pointIds.get(0),
        payload
    );
  }

  public HytaleRetrievedMemory retrieve(HytaleMemoryQuery query) {
    String projectKey = projectKeyFactory.projectKey(
        query.machineId(),
        query.clientProfileId(),
        query.serverTarget(),
        query.scenarioId()
    );
    Map<String, Object> scopeFilter = projectKeyFactory.semanticScope(
        query.machineId(),
        query.clientProfileId(),
        query.serverTarget(),
        query.scenarioId()
    );
    List<HytalePlaybook> playbooks = playbookRepository.listByScope(
        query.machineId(),
        query.clientProfileId(),
        query.serverTarget(),
        query.scenarioId(),
        true,
        20
    );
    List<HytaleVisualAnchor> anchors = visualAnchorRepository.listByScope(
        query.machineId(),
        query.clientProfileId(),
        query.serverTarget(),
        query.scenarioId(),
        20
    );
    List<HytalePromotionDecision> promotionCandidates = latestPromotionCandidates(
        query.machineId(),
        query.clientProfileId(),
        query.serverTarget(),
        query.scenarioId()
    );
    String queryText = query.queryText() == null || query.queryText().isBlank()
        ? fallbackQuery(query)
        : query.queryText();
    List<RetrievedSemanticContext> recoveryNotes = semanticMemoryService.searchProject(
        projectKey,
        queryText,
        10,
        semanticFilter(scopeFilter, "hytale-recovery-note")
    );
    List<RetrievedSemanticContext> scenarioSummaries = semanticMemoryService.searchProject(
        projectKey,
        queryText,
        10,
        semanticFilter(scopeFilter, "hytale-scenario-summary")
    );
    List<RetrievedSemanticContext> generalNotes = semanticMemoryService.searchProject(
        projectKey,
        queryText,
        10,
        scopeFilter
    );
    if (recoveryNotes.isEmpty()) {
      recoveryNotes = decisionFallback(promotionCandidates, "hytale-recovery-note");
    }
    if (scenarioSummaries.isEmpty()) {
      scenarioSummaries = decisionFallback(promotionCandidates, "hytale-scenario-summary");
    }
    if (generalNotes.isEmpty()) {
      generalNotes = promotionCandidates.stream()
          .map(decision -> new RetrievedSemanticContext(
              decision.decisionId(),
              0.5D,
              Map.of(
                  "summary", decision.summary(),
                  "semanticKind", decision.semanticKind(),
                  "decisionStatus", decision.decisionStatus()
              )
          ))
          .toList();
    }
    return new HytaleRetrievedMemory(
        playbooks,
        anchors,
        promotionCandidates,
        recoveryNotes,
        scenarioSummaries,
        generalNotes
    );
  }

  private PromotionEvaluation evaluatePromotion(HytalePromotionRequest request, HytaleLearningSession session) {
    if (request.subjectType() == null || request.subjectType().isBlank()) {
      throw new IllegalArgumentException("subjectType is required");
    }
    if (request.subjectId() == null || request.subjectId().isBlank()) {
      throw new IllegalArgumentException("subjectId is required");
    }
    if (request.semanticKind() == null || request.semanticKind().isBlank()) {
      throw new IllegalArgumentException("semanticKind is required");
    }
    if (request.summary() == null || request.summary().isBlank()) {
      throw new IllegalArgumentException("summary is required");
    }
    Map<String, Object> scopePayload = session == null
        ? projectKeyFactory.semanticScope("", "", "", "")
        : projectKeyFactory.semanticScope(
            session.machineId(),
            session.clientProfileId(),
            session.serverTarget(),
            session.scenarioId()
        );
    if ("playbook".equals(request.subjectType())) {
      HytalePlaybook playbook = playbookRepository.get(request.subjectId());
      if (!playbook.approved() && !playbook.pinned()) {
        return new PromotionEvaluation(false, "artifact-only", projectKeyFactory.projectKey(
            playbook.machineId(),
            playbook.clientProfileId(),
            playbook.serverTarget(),
            playbook.scenarioId()
        ), scopePayload);
      }
      return new PromotionEvaluation(true, "promoted", projectKeyFactory.projectKey(
          playbook.machineId(),
          playbook.clientProfileId(),
          playbook.serverTarget(),
          playbook.scenarioId()
      ), scopePayload);
    }
    boolean unstable = Boolean.TRUE.equals((request.metadata() == null ? Map.of() : request.metadata()).get("unstable"));
    return new PromotionEvaluation(
        !unstable,
        unstable ? "artifact-only" : "promoted",
        session == null ? projectKeyFactory.projectKey("", "", "", "") : projectKeyFactory.projectKey(session),
        scopePayload
    );
  }

  private List<HytalePromotionDecision> latestPromotionCandidates(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId
  ) {
    List<HytalePromotionDecision> decisions = new ArrayList<>();
    for (HytaleLearningSession session : learningSessionRepository.listByScope(
        machineId,
        clientProfileId,
        serverTarget,
        scenarioId,
        5
    )) {
      decisions.addAll(promotionDecisionRepository.listBySession(session.sessionId(), 5));
    }
    return decisions.stream().limit(20).toList();
  }

  private Map<String, Object> semanticFilter(Map<String, Object> scopeFilter, String semanticKind) {
    Map<String, Object> payload = new LinkedHashMap<>(scopeFilter);
    payload.put("semanticKind", semanticKind);
    return payload;
  }

  private SemanticContentType contentType(String semanticKind) {
    return switch (semanticKind) {
      case "hytale-scenario-summary", "hytale-recovery-note" -> SemanticContentType.RUN_SUMMARY;
      default -> SemanticContentType.GENERIC;
    };
  }

  private String fallbackQuery(HytaleMemoryQuery query) {
    return String.join(
        " ",
        blank(query.machineId()),
        blank(query.clientProfileId()),
        blank(query.serverTarget()),
        blank(query.scenarioId()),
        "hytale"
    ).trim();
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }

  private List<RetrievedSemanticContext> decisionFallback(
      List<HytalePromotionDecision> promotionCandidates,
      String semanticKind
  ) {
    return promotionCandidates.stream()
        .filter(decision -> semanticKind.equals(decision.semanticKind()))
        .map(decision -> new RetrievedSemanticContext(
            decision.decisionId(),
            0.5D,
            Map.of(
                "summary", decision.summary(),
                "semanticKind", decision.semanticKind(),
                "decisionStatus", decision.decisionStatus()
            )
        ))
        .toList();
  }

  private record PromotionEvaluation(
      boolean promotable,
      String decisionStatus,
      String projectKey,
      Map<String, Object> scopePayload
  ) {
  }
}
