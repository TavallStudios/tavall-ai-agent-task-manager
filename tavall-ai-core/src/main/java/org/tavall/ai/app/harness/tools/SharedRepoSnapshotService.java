package org.tavall.ai.app.harness.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class SharedRepoSnapshotService {

  public String createArchiveBase64(Path repoPath) {
    Path normalizedRepoPath = repoPath.toAbsolutePath().normalize();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
         ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
      Files.walkFileTree(normalizedRepoPath, new SnapshotWriter(normalizedRepoPath, zip));
      zip.finish();
      return Base64.getEncoder().encodeToString(output.toByteArray());
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create repo snapshot: " + exception.getMessage(), exception);
    }
  }

  public Path stageArchive(String repoName, String archiveBase64) {
    Path destinationRoot = sharedRepoRoot().resolve(safeName(repoName)).resolve(UUID.randomUUID().toString());
    byte[] archive = Base64.getDecoder().decode(archiveBase64);
    try {
      Files.createDirectories(destinationRoot);
      unpackArchive(destinationRoot, archive);
      return destinationRoot;
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to stage repo snapshot: " + exception.getMessage(), exception);
    }
  }

  private void unpackArchive(Path destinationRoot, byte[] archive) throws IOException {
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        Path destination = destinationRoot.resolve(entry.getName()).normalize();
        if (!destination.startsWith(destinationRoot)) {
          throw new IOException("Rejected repo snapshot entry outside destination root: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(destination);
        } else {
          Path parent = destination.getParent();
          if (parent != null) {
            Files.createDirectories(parent);
          }
          Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        input.closeEntry();
      }
    }
  }

  private Path sharedRepoRoot() {
    return Path.of(System.getProperty("java.io.tmpdir"), "tavall-ai-shared-repos");
  }

  private String safeName(String repoName) {
    String candidate = repoName == null || repoName.isBlank() ? "repo" : repoName.strip();
    return candidate.replaceAll("[^a-zA-Z0-9._-]+", "-");
  }

  private static final class SnapshotWriter extends SimpleFileVisitor<Path> {

    private final Path repoPath;
    private final ZipOutputStream zip;

    private SnapshotWriter(Path repoPath, ZipOutputStream zip) {
      this.repoPath = repoPath;
      this.zip = zip;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      if (!repoPath.equals(dir)) {
        String entryName = entryName(dir) + "/";
        zip.putNextEntry(new ZipEntry(entryName));
        zip.closeEntry();
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      zip.putNextEntry(new ZipEntry(entryName(file)));
      Files.copy(file, zip);
      zip.closeEntry();
      return FileVisitResult.CONTINUE;
    }

    private String entryName(Path path) {
      return repoPath.relativize(path).toString().replace('\\', '/');
    }
  }
}


