package com.agenttaskmanager.app.retrieval;

public record SemanticChunk(
    int chunkIndex,
    String chunkKind,
    int startLine,
    int endLine,
    String title,
    String text
) {
}
