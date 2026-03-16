package com.agenttaskmanager.app.knowledge;

public record KnowledgeChunk(
    String sourcePath,
    int chunkIndex,
    int startLine,
    int endLine,
    String text
) {
}
