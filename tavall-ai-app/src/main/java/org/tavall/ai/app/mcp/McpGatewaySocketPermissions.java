package org.tavall.ai.app.mcp;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class McpGatewaySocketPermissions {

  private static final Set<PosixFilePermission> OWNER_AND_GROUP_READ_WRITE =
      PosixFilePermissions.fromString("rw-rw----");

  private McpGatewaySocketPermissions() {
  }

  static void apply(Path socketPath, String groupName) throws IOException {
    if (groupName == null || groupName.isBlank()) {
      throw new IllegalArgumentException("socketGroup is required");
    }
    GroupPrincipal group = FileSystems.getDefault()
        .getUserPrincipalLookupService()
        .lookupPrincipalByGroupName(groupName.strip());
    Files.getFileAttributeView(socketPath, java.nio.file.attribute.PosixFileAttributeView.class)
        .setGroup(group);
    Files.setPosixFilePermissions(socketPath, OWNER_AND_GROUP_READ_WRITE);
  }
}
