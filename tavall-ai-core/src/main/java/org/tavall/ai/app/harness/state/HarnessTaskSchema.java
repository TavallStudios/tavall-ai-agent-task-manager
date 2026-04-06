package org.tavall.ai.app.harness.state;

import org.tavall.ai.app.model.orchestration.ArtifactRecord;
import org.tavall.ai.app.model.orchestration.CleanupReviewResult;
import org.tavall.ai.app.model.orchestration.OverseerDecisionRecord;
import org.tavall.ai.app.model.orchestration.OverseerTaskBatch;
import org.tavall.ai.app.model.orchestration.PatchDecisionRecord;
import org.tavall.ai.app.model.orchestration.SharedTaskContext;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.model.validation.ValidationReport;
import java.util.List;

public record HarnessTaskSchema(
    OverseerTaskBatch batch,
    List<WorkerTask> workerTasks,
    List<SharedTaskContext> sharedTaskContext,
    List<ArtifactRecord> taskArtifacts,
    List<CleanupReviewResult> cleanupReviews,
    List<ValidationReport> validationReports,
    List<OverseerDecisionRecord> overseerDecisions,
    List<PatchDecisionRecord> patchDecisions
) {
}

