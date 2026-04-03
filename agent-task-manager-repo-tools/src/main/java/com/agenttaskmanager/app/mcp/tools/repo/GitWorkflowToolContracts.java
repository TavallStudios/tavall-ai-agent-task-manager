package com.agenttaskmanager.app.mcp.tools.repo;

import java.util.List;

record GitWorkflowRequest(
    String repoPath,
    String changeType,
    String domain,
    String system,
    String user,
    String version,
    String summary,
    String details,
    String verification,
    Boolean finalChange,
    Boolean allowMixedDomain,
    List<String> filePaths,
    String domainOverride,
    String systemOverride,
    String userOverride,
    String versionOverride
) {
}

record GitCommitPlanResponse(
    String branchName,
    String subject,
    String body,
    List<String> candidateFiles,
    String groupingRecommendation,
    boolean mixedConcernDetected
) {
}

record PrepareGitBranchResponse(
    String branchName,
    String previousBranch,
    String currentHead,
    boolean created
) {
}

record CreateGitCommitResponse(
    String branchName,
    String commitHash,
    String subject,
    String body,
    List<String> committedFiles,
    String groupingRecommendation
) {
}

