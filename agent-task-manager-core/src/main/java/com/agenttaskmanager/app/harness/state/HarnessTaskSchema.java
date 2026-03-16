package com.agenttaskmanager.app.harness.state;

import com.agenttaskmanager.app.model.orchestration.ArtifactRecord;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.OverseerDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.PatchDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.SharedTaskContext;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.validation.ValidationReport;
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
