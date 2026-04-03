package com.agenttaskmanager.app.mcp.tools.repo;

record StageSharedRepoSnapshotRequest(String repoName, String archiveBase64) {
}

record StageSharedRepoSnapshotResponse(String repoPath) {
}
