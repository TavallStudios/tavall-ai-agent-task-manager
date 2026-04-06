package org.tavall.ai.app.retrieval;

public record SemanticChunk(
    int chunkIndex,
    String chunkKind,
    int startLine,
    int endLine,
    String title,
    String text
) {
}

