package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.config.RepoCatalogProperties;
import com.agenttaskmanager.app.model.KnownRepo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RepoCatalogService {

  private final RepoCatalogProperties properties;

  public RepoCatalogService(RepoCatalogProperties properties) {
    this.properties = properties;
  }

  public List<KnownRepo> listRepos() {
    List<RawRepo> rawRepos = loadRawRepos();
    Map<String, Long> keyCounts = rawRepos.stream()
        .collect(Collectors.groupingBy(raw -> slugify(raw.displayName()), LinkedHashMap::new, Collectors.counting()));

    Set<String> assignedKeys = new LinkedHashSet<>();
    List<KnownRepo> repos = new ArrayList<>();
    for (RawRepo rawRepo : rawRepos) {
      String baseKey = slugify(rawRepo.displayName());
      String projectKey = baseKey;
      if (keyCounts.getOrDefault(baseKey, 0L) > 1) {
        projectKey = baseKey + "-" + slugify(rawRepo.locationLabel());
      }
      if (!assignedKeys.add(projectKey)) {
        projectKey = projectKey + "-" + shortPathHash(rawRepo.repoPath());
        assignedKeys.add(projectKey);
      }
      repos.add(new KnownRepo(
          rawRepo.displayName(),
          projectKey,
          rawRepo.repoPath().toString(),
          rawRepo.locationLabel()
      ));
    }
    return repos;
  }

  public KnownRepo requireByPath(String repoPath) {
    if (!StringUtils.hasText(repoPath)) {
      throw new IllegalArgumentException("Repository path is required.");
    }
    Path normalizedPath = Path.of(repoPath).normalize();
    String normalized = normalizedPath.toString();
    return listRepos().stream()
        .filter(repo -> repo.repoPath().equals(normalized))
        .findFirst()
        .orElseGet(() -> fallbackRepo(normalizedPath));
  }

  private List<RawRepo> loadRawRepos() {
    return properties.getRoots().stream()
        .map(Path::of)
        .filter(Files::isDirectory)
        .flatMap(this::scanRoot)
        .filter(Objects::nonNull)
        .distinct()
        .sorted(Comparator
            .comparingInt((RawRepo rawRepo) -> locationRank(rawRepo.locationLabel()))
            .thenComparing(RawRepo::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(rawRepo -> rawRepo.repoPath().toString(), String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private Stream<RawRepo> scanRoot(Path root) {
    try (Stream<Path> paths = Files.find(root, properties.getMaxDepth(), this::isGitDirectory)) {
      return paths
          .map(Path::getParent)
          .filter(Objects::nonNull)
          .map(Path::normalize)
          .map(repoPath -> new RawRepo(
              repoPath,
              repoPath.getFileName() == null ? repoPath.toString() : repoPath.getFileName().toString(),
              describeLocation(root)
          ))
          .toList()
          .stream();
    } catch (IOException | UncheckedIOException ignored) {
      return Stream.empty();
    }
  }

  private boolean isGitDirectory(Path path, java.nio.file.attribute.BasicFileAttributes attributes) {
    return attributes.isDirectory()
        && path.getFileName() != null
        && ".git".equals(path.getFileName().toString());
  }

  private static int locationRank(String locationLabel) {
    if ("remote".equals(locationLabel)) {
      return 0;
    }
    if ("workspace".equals(locationLabel)) {
      return 1;
    }
    return 2;
  }

  private static String describeLocation(Path root) {
    String normalized = root.normalize().toString();
    if (normalized.equals(currentWorkingDirectory())) {
      return "workspace";
    }
    if (!isWindows() && "/srv".equals(normalized)) {
      return "remote";
    }
    Path fileName = root.getFileName();
    return fileName == null ? normalized : fileName.toString();
  }

  private static String currentWorkingDirectory() {
    return Path.of(System.getProperty("user.dir", ".")).normalize().toString();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static String slugify(String rawValue) {
    String value = rawValue == null ? "" : rawValue.strip().toLowerCase(Locale.ROOT);
    value = value.replaceAll("[^a-z0-9]+", "-");
    value = value.replaceAll("^-+|-+$", "");
    return value.isEmpty() ? "repo" : value;
  }

  private static String shortPathHash(Path repoPath) {
    String hex = Integer.toUnsignedString(repoPath.toString().hashCode(), 16);
    return hex.length() <= 6 ? hex : hex.substring(0, 6);
  }

  private KnownRepo fallbackRepo(Path repoPath) {
    if (Files.isDirectory(repoPath.resolve(".git"))) {
      String displayName = repoPath.getFileName() == null ? repoPath.toString() : repoPath.getFileName().toString();
      return new KnownRepo(
          displayName,
          slugify(displayName) + "-" + shortPathHash(repoPath),
          repoPath.toString(),
          "ad-hoc"
      );
    }
    throw new IllegalArgumentException("Repository is not in the configured catalog: " + repoPath);
  }

  private record RawRepo(Path repoPath, String displayName, String locationLabel) {
  }
}
