package org.tavall.ai.app.concurrent;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;

public record BatchResult<T>(
    List<Outcome<T>> outcomes,
    boolean cancelled,
    boolean timedOut
) {

  public List<T> successes() {
    return outcomes.stream()
        .filter(outcome -> outcome.state() == StructuredTaskScope.Subtask.State.SUCCESS)
        .map(Outcome::result)
        .toList();
  }

  public List<Throwable> failures() {
    return outcomes.stream()
        .filter(outcome -> outcome.state() == StructuredTaskScope.Subtask.State.FAILED)
        .map(Outcome::error)
        .toList();
  }

  public boolean hasFailures() {
    return outcomes.stream().anyMatch(outcome -> outcome.state() == StructuredTaskScope.Subtask.State.FAILED);
  }

  public Throwable firstFailureOrNull() {
    return outcomes.stream()
        .filter(outcome -> outcome.state() == StructuredTaskScope.Subtask.State.FAILED)
        .map(Outcome::error)
        .findFirst()
        .orElse(null);
  }
}

