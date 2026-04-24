package org.tavall.ai.app.model.hytalelearning;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
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

