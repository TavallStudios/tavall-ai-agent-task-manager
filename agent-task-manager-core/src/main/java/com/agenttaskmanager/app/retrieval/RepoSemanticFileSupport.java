package com.agenttaskmanager.app.retrieval;

import com.agenttaskmanager.app.model.KnownRepo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RepoSemanticFileSupport {

  public boolean isIndexable(Path path) {
    String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return lower.endsWith(".java")
        || lower.endsWith(".kt")
        || lower.endsWith(".groovy")
        || lower.endsWith(".js")
        || lower.endsWith(".ts")
        || lower.endsWith(".tsx")
        || lower.endsWith(".jsx")
        || lower.endsWith(".py")
        || lower.endsWith(".cs")
        || lower.endsWith(".cpp")
        || lower.endsWith(".c")
        || lower.endsWith(".h")
        || lower.endsWith(".hpp")
        || lower.endsWith(".md")
        || lower.endsWith(".txt")
        || lower.endsWith(".json")
        || lower.endsWith(".yml")
        || lower.endsWith(".yaml")
        || lower.endsWith(".xml")
        || lower.endsWith(".properties");
  }

  public boolean isSyncCandidate(Path repoRoot, Path file) {
    if (!Files.isRegularFile(file)) {
      return false;
    }
    String relativePath = relativePath(repoRoot, file);
    return !isExcludedRelativePath(relativePath) && isIndexable(file);
  }

  public SemanticDocumentRequest buildRequest(KnownRepo repo, Path repoRoot, Path file) throws IOException {
    String relativePath = relativePath(repoRoot, file);
    SemanticContentType contentType = classifyFile(relativePath);
    SemanticCollectionDomain domain = contentType == SemanticContentType.CODE
        ? SemanticCollectionDomain.CODE_REPO
        : SemanticCollectionDomain.KNOWLEDGE_RULES;
    return new SemanticDocumentRequest(
        SemanticVectorStoreService.deterministicDocumentId(repo.projectKey() + ":" + relativePath),
        null,
        null,
        "project-reindex",
        relativePath,
        Files.readString(file, StandardCharsets.UTF_8),
        domain,
        contentType,
        java.util.Map.of(
            "sourcePath", relativePath,
            "sourceRepo", repo.displayName(),
            "locationLabel", repo.locationLabel()
        )
    );
  }

  public String relativePath(Path repoRoot, Path file) {
    return repoRoot.relativize(file).toString().replace('\\', '/');
  }

  public boolean isExcludedRelativePath(String relativePath) {
    String normalized = relativePath == null ? "" : relativePath.strip().replace('\\', '/');
    return normalized.isBlank()
        || ".git".equals(normalized)
        || normalized.startsWith(".git/")
        || normalized.startsWith(".agent-task-manager.");
  }

  public String upsertDedupeKey(KnownRepo repo, String relativePath) {
    return "project-upsert:" + repo.projectKey() + ":" + relativePath;
  }

  public String deleteDedupeKey(KnownRepo repo, String relativePath) {
    return "project-delete:" + repo.projectKey() + ":" + relativePath;
  }

  public String domainDeleteDedupeKey(String projectKey, SemanticCollectionDomain domain) {
    return "project-delete-domain:" + projectKey + ":" + domain.name();
  }

  private SemanticContentType classifyFile(String relativePath) {
    String lower = relativePath.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".md")
        || lower.endsWith(".txt")
        || lower.endsWith(".json")
        || lower.endsWith(".yml")
        || lower.endsWith(".yaml")
        || lower.endsWith(".xml")
        || lower.endsWith(".properties")) {
      return SemanticContentType.DOCUMENTATION;
    }
    return SemanticContentType.CODE;
  }
}
