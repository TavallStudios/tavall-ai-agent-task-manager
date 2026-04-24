package org.tavall.ai.app.mcp.tools.computeruse;

import org.tavall.ai.app.model.computeruse.ComputerUseCaptureRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseCaptureResult;
import org.tavall.ai.app.model.computeruse.ComputerUseInputBatch;
import org.tavall.ai.app.model.computeruse.ComputerUseProcessLaunchRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseProcessLaunchResult;
import org.tavall.ai.app.model.computeruse.ComputerUseRunnerRegistration;
import org.tavall.ai.app.model.computeruse.ComputerUseRunnerSummary;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionSummary;
import org.tavall.ai.app.model.computeruse.ComputerUseVisionMatch;
import org.tavall.ai.app.model.computeruse.ComputerUseVisionWaitRequest;
import java.util.List;
import java.util.Map;

record ComputerUseRunnerSummaryResponse(ComputerUseRunnerSummary runner) {
}

record ComputerUseRunnerListResponse(List<ComputerUseRunnerSummary> runners) {
}

record ComputerUseSessionSummaryResponse(ComputerUseSessionSummary session) {
}

record ComputerUseCaptureResponse(ComputerUseCaptureResult capture) {
}

record ComputerUseVisionMatchResponse(ComputerUseVisionMatch match) {
}

record ComputerUseProcessLaunchResponse(ComputerUseProcessLaunchResult launch) {
}

record ComputerUseInputResponse(Map<String, Object> result) {
}

record RegisterComputerUseRunnerRequest(
    String runnerId,
    String displayName,
    String hostName,
    String baseUrl,
    String launcherPath,
    String clientPath,
    List<String> supportedCaptureModes,
    Map<String, Object> capabilities,
    Map<String, Object> metadata
) {
  ComputerUseRunnerRegistration toRegistration() {
    return new ComputerUseRunnerRegistration(
        runnerId,
        displayName,
        hostName,
        baseUrl,
        launcherPath,
        clientPath,
        supportedCaptureModes,
        capabilities,
        metadata
    );
  }
}

record StartComputerUseSessionRequest(
    String runnerId,
    String taskId,
    String workerTaskId,
    String scenarioId,
    String serverTarget,
    String chartId,
    List<String> expectedArtifacts,
    List<String> passFailGates,
    Map<String, Object> artifactPolicy,
    Map<String, Object> metadata
) {
  ComputerUseSessionRequest toSessionRequest() {
    return new ComputerUseSessionRequest(
        runnerId,
        taskId,
        workerTaskId,
        scenarioId,
        serverTarget,
        chartId,
        expectedArtifacts,
        passFailGates,
        artifactPolicy,
        metadata
    );
  }
}

record LaunchComputerUseProcessRequest(
    String sessionId,
    String launchTarget,
    String fileName,
    String arguments,
    String workingDirectory,
    int waitForWindowMs,
    String windowTitleContains
) {
  ComputerUseProcessLaunchRequest toLaunchRequest() {
    return new ComputerUseProcessLaunchRequest(
        sessionId,
        launchTarget,
        fileName,
        arguments,
        workingDirectory,
        waitForWindowMs,
        windowTitleContains
    );
  }
}

record CaptureComputerUseWindowRequest(
    String sessionId,
    String titleContains,
    String processName,
    boolean storeArtifact,
    String artifactKind,
    String summary
) {
  ComputerUseCaptureRequest toCaptureRequest() {
    return new ComputerUseCaptureRequest(sessionId, titleContains, processName, storeArtifact, artifactKind, summary);
  }
}

record SendComputerUseInputRequest(String sessionId, ComputerUseInputBatch inputBatch) {
}

record WaitForComputerUseVisionMatchRequest(
    String sessionId,
    String anchorKey,
    String templatePath,
    String titleContains,
    String processName,
    double threshold,
    int timeoutMs,
    boolean storeArtifact
) {
  ComputerUseVisionWaitRequest toVisionRequest() {
    return new ComputerUseVisionWaitRequest(
        sessionId,
        anchorKey,
        templatePath,
        titleContains,
        processName,
        threshold,
        timeoutMs,
        storeArtifact
    );
  }
}

record StopComputerUseSessionRequest(String sessionId) {
}

