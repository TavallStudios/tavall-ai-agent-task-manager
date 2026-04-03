package com.agenttaskmanager.app.model.hytalelearning;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import java.util.List;

public record HytaleRetrievedMemory(
    List<HytalePlaybook> approvedPlaybooks,
    List<HytaleVisualAnchor> visualAnchors,
    List<HytalePromotionDecision> promotionCandidates,
    List<RetrievedSemanticContext> recoveryNotes,
    List<RetrievedSemanticContext> scenarioSummaries,
    List<RetrievedSemanticContext> generalNotes
) {
}
