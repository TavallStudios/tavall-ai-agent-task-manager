package com.agenttaskmanager.app.knowledge;

import com.agenttaskmanager.app.config.KnowledgeIndexProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeChunker {

  private final KnowledgeIndexProperties properties;

  public KnowledgeChunker(KnowledgeIndexProperties properties) {
    this.properties = properties;
  }

  public List<KnowledgeChunk> chunk(String sourcePath, String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    List<String> lines = content.lines().toList();
    if (lines.isEmpty()) {
      return List.of();
    }
    List<KnowledgeChunk> chunks = new ArrayList<>();
    int startLineIndex = 0;
    int chunkIndex = 0;
    while (startLineIndex < lines.size()) {
      StringBuilder builder = new StringBuilder();
      int endLineIndex = startLineIndex;
      while (endLineIndex < lines.size()) {
        String line = lines.get(endLineIndex);
        String candidate = builder.isEmpty() ? line : builder + "\n" + line;
        boolean tooLong = candidate.length() > properties.getMaxChunkChars();
        boolean tooManyLines = endLineIndex - startLineIndex >= properties.getTargetChunkLines();
        if ((tooLong || tooManyLines) && endLineIndex > startLineIndex) {
          break;
        }
        if (!builder.isEmpty()) {
          builder.append('\n');
        }
        builder.append(line);
        endLineIndex++;
      }
      chunks.add(new KnowledgeChunk(
          sourcePath,
          chunkIndex++,
          startLineIndex + 1,
          endLineIndex,
          builder.toString().strip()
      ));
      if (endLineIndex >= lines.size()) {
        break;
      }
      startLineIndex = Math.max(startLineIndex + 1, endLineIndex - properties.getOverlapLines());
    }
    return chunks;
  }
}
