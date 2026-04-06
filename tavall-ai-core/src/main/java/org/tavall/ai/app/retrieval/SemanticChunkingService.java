package org.tavall.ai.app.retrieval;

import org.tavall.ai.app.config.SemanticIndexProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SemanticChunkingService {

  private static final Pattern DOC_HEADER = Pattern.compile("^#{1,6}\\s+.+$");
  private static final Pattern CHAT_SPEAKER = Pattern.compile("^\\s*(system|user|assistant|developer)\\s*:", Pattern.CASE_INSENSITIVE);
  private static final Pattern RUN_BLOCK = Pattern.compile("^(\\d+\\.|[-*])\\s+.+$");
  private static final Pattern CODE_HEADER = Pattern.compile(
      "^\\s*((public|protected|private|static|final|abstract|sealed|non-sealed|synchronized|native|async|export)\\s+)*"
          + "(class|interface|enum|record|fun|function)\\b.*|"
          + "^\\s*((public|protected|private|static|final|abstract|synchronized|native|async)\\s+)*"
          + "[\\w<>,\\[\\]?]+\\s+[\\w$]+\\s*\\([^;]*\\)\\s*(\\{|=>).*$|"
          + "^\\s*(const|let|var)\\s+[\\w$]+\\s*=\\s*(async\\s*)?\\([^=;]*\\)\\s*=>.*$"
  );

  private final SemanticIndexProperties properties;

  public SemanticChunkingService(SemanticIndexProperties properties) {
    this.properties = properties;
  }

  public List<SemanticChunk> chunk(SemanticDocumentRequest request) {
    String content = request.content() == null ? "" : request.content().strip();
    if (content.isBlank()) {
      return List.of();
    }
    return switch (request.contentType()) {
      case DOCUMENTATION -> chunkByPattern(request.title(), content, DOC_HEADER, "doc-section", properties.getDocTargetChunkLines());
      case CHAT -> chunkChat(request.title(), content);
      case CODE -> chunkCode(request.title(), content);
      case DIFF -> chunkDiff(request.title(), content);
      case RUN_SUMMARY -> chunkByPattern(request.title(), content, RUN_BLOCK, "run-block", properties.getDocTargetChunkLines());
      case GENERIC -> lineWindowChunks(request.title(), content, "text-window", properties.getTextTargetChunkLines());
    };
  }

  private List<SemanticChunk> chunkCode(String title, String content) {
    List<SemanticChunk> chunks = chunkByPattern(title, content, CODE_HEADER, "code-symbol", properties.getCodeTargetChunkLines());
    return chunks.size() <= 1 ? lineWindowChunks(title, content, "code-window", properties.getCodeTargetChunkLines()) : chunks;
  }

  private List<SemanticChunk> chunkDiff(String title, String content) {
    List<String> lines = content.lines().toList();
    List<Range> ranges = new ArrayList<>();
    int start = 0;
    for (int index = 1; index < lines.size(); index++) {
      String line = lines.get(index);
      if (line.startsWith("diff --git ") || line.startsWith("@@ ")) {
        ranges.add(new Range(start, index));
        start = index;
      }
    }
    ranges.add(new Range(start, lines.size()));
    return toChunks(title, lines, ranges, "diff-hunk", properties.getCodeTargetChunkLines());
  }

  private List<SemanticChunk> chunkChat(String title, String content) {
    List<String> lines = content.lines().toList();
    List<Range> messages = new ArrayList<>();
    int start = 0;
    for (int index = 1; index < lines.size(); index++) {
      if (CHAT_SPEAKER.matcher(lines.get(index)).find()) {
        messages.add(new Range(start, index));
        start = index;
      }
    }
    messages.add(new Range(start, lines.size()));
    List<SemanticChunk> chunks = new ArrayList<>();
    int step = Math.max(1, properties.getChatWindowMessages() - properties.getChatWindowOverlap());
    for (int index = 0; index < messages.size(); index += step) {
      int endIndex = Math.min(messages.size(), index + properties.getChatWindowMessages());
      Range startRange = messages.get(index);
      Range endRange = messages.get(endIndex - 1);
      chunks.add(new SemanticChunk(
          chunks.size(),
          "chat-window",
          startRange.start() + 1,
          endRange.end(),
          chunkTitle(title, chunks.size() + 1),
          joinLines(lines, startRange.start(), endRange.end())
      ));
      if (endIndex >= messages.size()) {
        break;
      }
    }
    return chunks;
  }

  private List<SemanticChunk> chunkByPattern(
      String title,
      String content,
      Pattern boundaryPattern,
      String chunkKind,
      int targetLines
  ) {
    List<String> lines = content.lines().toList();
    List<Range> ranges = new ArrayList<>();
    int start = 0;
    for (int index = 1; index < lines.size(); index++) {
      if (boundaryPattern.matcher(lines.get(index)).find()) {
        ranges.add(new Range(start, index));
        start = index;
      }
    }
    ranges.add(new Range(start, lines.size()));
    if (ranges.size() == 1) {
      return lineWindowChunks(title, content, chunkKind, targetLines);
    }
    return toChunks(title, lines, ranges, chunkKind, targetLines);
  }

  private List<SemanticChunk> toChunks(
      String title,
      List<String> lines,
      List<Range> ranges,
      String chunkKind,
      int targetLines
  ) {
    List<SemanticChunk> chunks = new ArrayList<>();
    for (Range range : ranges) {
      if (range.start() >= range.end()) {
        continue;
      }
      String text = joinLines(lines, range.start(), range.end());
      if (text.length() <= properties.getMaxChunkChars() && range.size() <= targetLines) {
        chunks.add(new SemanticChunk(
            chunks.size(),
            chunkKind,
            range.start() + 1,
            range.end(),
            chunkTitle(title, chunks.size() + 1),
            text
        ));
        continue;
      }
      chunks.addAll(splitRange(title, lines, range, chunkKind, targetLines, chunks.size()));
    }
    return chunks;
  }

  private List<SemanticChunk> splitRange(
      String title,
      List<String> lines,
      Range range,
      String chunkKind,
      int targetLines,
      int chunkIndexOffset
  ) {
    List<SemanticChunk> chunks = new ArrayList<>();
    int start = range.start();
    int chunkIndex = chunkIndexOffset;
    while (start < range.end()) {
      int end = buildEndIndex(lines, start, targetLines, range.end());
      chunks.add(new SemanticChunk(
          chunkIndex++,
          chunkKind,
          start + 1,
          end,
          chunkTitle(title, chunkIndex),
          joinLines(lines, start, end)
      ));
      if (end >= range.end()) {
        break;
      }
      start = Math.max(start + 1, end - properties.getOverlapLines());
    }
    return chunks;
  }

  private List<SemanticChunk> lineWindowChunks(String title, String content, String chunkKind, int targetLines) {
    List<String> lines = content.lines().toList();
    List<SemanticChunk> chunks = new ArrayList<>();
    int start = 0;
    while (start < lines.size()) {
      int end = buildEndIndex(lines, start, targetLines, lines.size());
      chunks.add(new SemanticChunk(
          chunks.size(),
          chunkKind,
          start + 1,
          end,
          chunkTitle(title, chunks.size() + 1),
          joinLines(lines, start, end)
      ));
      if (end >= lines.size()) {
        break;
      }
      start = Math.max(start + 1, end - properties.getOverlapLines());
    }
    return chunks;
  }

  private int buildEndIndex(List<String> lines, int start, int targetLines, int maxExclusive) {
    StringBuilder builder = new StringBuilder();
    int end = start;
    while (end < maxExclusive) {
      String candidate = builder.isEmpty() ? lines.get(end) : builder + "\n" + lines.get(end);
      if ((candidate.length() > properties.getMaxChunkChars() || end - start >= targetLines) && end > start) {
        break;
      }
      if (!builder.isEmpty()) {
        builder.append('\n');
      }
      builder.append(lines.get(end));
      end++;
    }
    return end;
  }

  private String joinLines(List<String> lines, int start, int end) {
    return String.join("\n", lines.subList(start, end)).strip();
  }

  private String chunkTitle(String title, int chunkNumber) {
    String base = title == null || title.isBlank() ? "semantic chunk" : title.strip();
    return base + " [chunk " + chunkNumber + "]";
  }

  private record Range(int start, int end) {
    int size() {
      return end - start;
    }
  }
}

