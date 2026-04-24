package org.tavall.ai.app.validation;

import java.nio.file.Path;
import spoon.Launcher;
import spoon.reflect.CtModel;

public class SpoonModelFactory {

  public CtModel loadModel(Path repoRoot) {
    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setComplianceLevel(21);
    launcher.addInputResource(repoRoot.resolve("src/main/java").toString());
    launcher.addInputResource(repoRoot.resolve("src/test/java").toString());
    launcher.buildModel();
    return launcher.getModel();
  }
}

