package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.nio.file.Path;
import java.util.List;

public record JavaCompiledArtifacts(
    String status,
    List<Path> moduleRoots,
    List<Path> classpathRoots,
    String output
) {
}
