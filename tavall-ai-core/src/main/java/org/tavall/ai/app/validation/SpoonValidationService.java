package org.tavall.ai.app.validation;

import org.tavall.ai.app.model.validation.ValidationViolation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import spoon.reflect.CtModel;

@Service
public class SpoonValidationService {

  private final SpoonModelFactory spoonModelFactory = new SpoonModelFactory();
  private final SpoonCodeRuleSet spoonCodeRuleSet = new SpoonCodeRuleSet();
  private final SpoonTestRuleSet spoonTestRuleSet = new SpoonTestRuleSet();

  public List<ValidationViolation> runValidation(Path repoRoot) {
    CtModel model = spoonModelFactory.loadModel(repoRoot);
    List<ValidationViolation> violations = new ArrayList<>();
    violations.addAll(spoonCodeRuleSet.collectViolations(model));
    violations.addAll(spoonTestRuleSet.collectViolations(repoRoot));
    return violations;
  }
}

