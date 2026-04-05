package com.agenttaskmanager.app.concurrent;

import com.agenttaskmanager.app.console.Log;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

public class AsyncTask {

  private static final AtomicLong VT_COUNTER = new AtomicLong();

  private AsyncTask() {
  }

  /**
   * Configuration knobs for the scope. Mirrors what the JDK exposes: thread factory, name, timeout.
   */
  public record ScopeOptions(
      ThreadFactory threadFactory,
      String name,
      Duration timeout
  ) {

    public static ScopeOptions defaults() {
      return new ScopeOptions(null, null, null);
    }

    public ScopeOptions withThreadFactory(ThreadFactory threadFactory) {
      return new ScopeOptions(threadFactory, name, timeout);
    }

    public ScopeOptions withName(String name) {
      return new ScopeOptions(threadFactory, name, timeout);
    }

    public ScopeOptions withTimeout(Duration timeout) {
      return new ScopeOptions(threadFactory, name, timeout);
    }
  }

  /**
   * Runs one task "async inside, sync outside": fork in a new (virtual) thread, join, return result.
   * Uses Joiner.anySuccessfulResultOrThrow() (single task: result-or-throw).
   */
  public static <T> T runAsync(Callable<? extends T> task, ScopeOptions options)
      throws InterruptedException, StructuredTaskScope.TimeoutException, StructuredTaskScope.FailedException {
    Objects.requireNonNull(task, "task");
    ScopeOptions opt = options == null ? ScopeOptions.defaults() : options;
    Log.info("AsyncTask runAsync start: {} on {}", taskLabel(task, opt), Thread.currentThread().getName());

    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<T>anySuccessfulResultOrThrow(), configFn(opt))) {
      scope.fork(task::call);
      return scope.join();
    }
  }

  public static <T> T runAsync(Callable<? extends T> task)
      throws InterruptedException, StructuredTaskScope.TimeoutException, StructuredTaskScope.FailedException {
    return runAsync(task, ScopeOptions.defaults());
  }

  /**
   * Runs tasks concurrently and returns results, FAIL-FAST style (throws if any task fails).
   * This is the clean "all must succeed" policy.
   */
  public static <T> List<T> runMultipleAsync(Collection<? extends Callable<? extends T>> tasks, ScopeOptions options)
      throws InterruptedException, StructuredTaskScope.TimeoutException, StructuredTaskScope.FailedException {
    Objects.requireNonNull(tasks, "tasks");
    if (tasks.isEmpty()) {
      return List.of();
    }
    ScopeOptions opt = options == null ? ScopeOptions.defaults() : options;
    Log.info(
        "AsyncTask runMultipleAsync start: {} count={} on {}",
        taskLabel(null, opt),
        tasks.size(),
        Thread.currentThread().getName()
    );

    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<T>allSuccessfulOrThrow(), configFn(opt))) {
      for (var task : tasks) {
        scope.fork(task::call);
      }
      return scope.join().map(StructuredTaskScope.Subtask::get).toList();
    }
  }

  public static <T> List<T> runMultipleAsync(Collection<? extends Callable<? extends T>> tasks)
      throws InterruptedException, StructuredTaskScope.TimeoutException, StructuredTaskScope.FailedException {
    return runMultipleAsync(tasks, ScopeOptions.defaults());
  }

  /**
   * The "boolean knobs" batch runner:
   * <p>
   * cancelAfterFailures:
   * - 0 => never cancel early (wait for all)
   * - 1 => cancel on first failure (fail-fast cancellation)
   * - N => cancel after N failures
   * <p>
   * throwOnFailure:
   * - true => throw BatchFailedException if any FAILED outcomes exist
   * - false => always return BatchResult
   * <p>
   * Uses Joiner.allUntil(predicate): cancels scope when predicate returns true, and still yields all subtasks
   * (some may be UNAVAILABLE).
   */
  public static <T> BatchResult<T> runMultipleAsync(
      List<? extends Callable<? extends T>> tasks,
      ScopeOptions options,
      int cancelAfterFailures,
      boolean throwOnFailure
  ) throws InterruptedException, StructuredTaskScope.TimeoutException, BatchFailedException {
    Objects.requireNonNull(tasks, "tasks");
    if (tasks.isEmpty()) {
      return new BatchResult<>(List.of(), false, false);
    }

    ScopeOptions opt = options == null ? ScopeOptions.defaults() : options;
    int threshold = Math.max(0, cancelAfterFailures);

    AtomicInteger failureCount = new AtomicInteger(0);
    Predicate<StructuredTaskScope.Subtask<? extends T>> cancelPredicate = subtask -> {
      if (threshold == 0) {
        return false;
      }
      if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
        return failureCount.incrementAndGet() >= threshold;
      }
      return false;
    };

    List<StructuredTaskScope.Subtask<T>> forked = new ArrayList<>(tasks.size());
    StructuredTaskScope.TimeoutException timeoutEx = null;

    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<T>allUntil(cancelPredicate), configFn(opt))) {
      for (var task : tasks) {
        @SuppressWarnings("unchecked")
        Callable<T> cast = (Callable<T>) task;
        forked.add(scope.fork(cast));
      }

      List<StructuredTaskScope.Subtask<T>> finishedInOrder;
      try {
        finishedInOrder = scope.join().toList();
      } catch (StructuredTaskScope.TimeoutException exception) {
        timeoutEx = exception;
        finishedInOrder = forked;
      }

      var outcomes = new ArrayList<Outcome<T>>(finishedInOrder.size());
      for (int i = 0; i < finishedInOrder.size(); i++) {
        StructuredTaskScope.Subtask<T> subtask = finishedInOrder.get(i);
        StructuredTaskScope.Subtask.State state = subtask.state();
        T value = null;
        Throwable err = null;

        if (state == StructuredTaskScope.Subtask.State.SUCCESS) {
          value = subtask.get();
        } else if (state == StructuredTaskScope.Subtask.State.FAILED) {
          err = subtask.exception();
        }

        outcomes.add(new Outcome<>(i, state, value, err));
      }

      BatchResult<T> result = new BatchResult<>(outcomes, scope.isCancelled(), timeoutEx != null);

      if (throwOnFailure && result.hasFailures()) {
        throw new BatchFailedException(
            "One or more subtasks failed",
            result.firstFailureOrNull(),
            result
        );
      }

      if (timeoutEx != null) {
        throw timeoutEx;
      }

      return result;
    }
  }

  /**
   * "First success wins" helper: cancels the other subtasks when one succeeds.
   */
  public static <T> T runAnySuccessAsync(Collection<? extends Callable<? extends T>> tasks, ScopeOptions options)
      throws InterruptedException, StructuredTaskScope.TimeoutException, StructuredTaskScope.FailedException {
    Objects.requireNonNull(tasks, "tasks");
    if (tasks.isEmpty()) {
      throw new IllegalArgumentException("tasks must not be empty");
    }

    ScopeOptions opt = options == null ? ScopeOptions.defaults() : options;
    Log.info(
        "AsyncTask runAnySuccessAsync start: {} count={} on {}",
        taskLabel(null, opt),
        tasks.size(),
        Thread.currentThread().getName()
    );

    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<T>anySuccessfulResultOrThrow(), configFn(opt))) {
      for (var task : tasks) {
        scope.fork(task::call);
      }
      return scope.join();
    }
  }

  // Non-lock
  public static <T> CompletableFuture<T> runFuture(Callable<? extends T> task, ScopeOptions options) {
    Objects.requireNonNull(task, "task");
    ScopeOptions opt = options == null ? ScopeOptions.defaults() : options;

    CompletableFuture<T> future = new CompletableFuture<>();

    Log.info("AsyncTask runFuture scheduled: {}", taskLabel(task, opt));
    newThread(opt.name != null ? opt.name : "async-task", () -> {
      try {
        T value = runAsync(task, opt);
        future.complete(value);
      } catch (Throwable t) {
        future.completeExceptionally(t);
      }
    });

    return future;
  }

  public static <T> CompletableFuture<T> runFuture(Callable<? extends T> task) {
    return runFuture(task, ScopeOptions.defaults());
  }

  public static Thread newThread(String baseName, Runnable runnable) {
    String name = baseName + "-" + VT_COUNTER.incrementAndGet();
    Thread thread = Thread.ofVirtual().name(name).start(runnable);
    Log.info("AsyncTask thread started: {}", name);
    return thread;
  }

  public static String unwrapMessage(Throwable ex) {
    Throwable t = ex;
    while (t.getCause() != null && t != t.getCause()) {
      t = t.getCause();
    }
    return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
  }

  private static Function<StructuredTaskScope.Configuration, StructuredTaskScope.Configuration> configFn(ScopeOptions opt) {
    return configuration -> {
      StructuredTaskScope.Configuration cfg = configuration;
      if (opt.threadFactory != null) {
        cfg = cfg.withThreadFactory(opt.threadFactory);
      }
      if (opt.name != null) {
        cfg = cfg.withName(opt.name);
      }
      if (opt.timeout != null) {
        cfg = cfg.withTimeout(opt.timeout);
      }
      return cfg;
    };
  }

  private static String taskLabel(Callable<?> task, ScopeOptions opt) {
    if (opt != null && opt.name != null && !opt.name.isBlank()) {
      return opt.name;
    }
    if (task == null) {
      return "async-batch";
    }
    return task.getClass().getSimpleName();
  }
}
