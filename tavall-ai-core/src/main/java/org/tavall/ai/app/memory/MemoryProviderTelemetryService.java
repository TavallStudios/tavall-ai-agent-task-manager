package org.tavall.ai.app.memory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Service;

@Service
public class MemoryProviderTelemetryService {

  private final Map<String, LongAdder> calls = new ConcurrentHashMap<>();
  private final Map<String, LongAdder> degradedCalls = new ConcurrentHashMap<>();
  private final Map<String, LongAdder> latencyMillis = new ConcurrentHashMap<>();
  private final Map<String, LongAdder> contextCharacters = new ConcurrentHashMap<>();

  public void record(MemoryKnowledgeContext context) {
    String provider = context.provider();
    calls.computeIfAbsent(provider, ignored -> new LongAdder()).increment();
    latencyMillis.computeIfAbsent(provider, ignored -> new LongAdder()).add(context.latencyMillis());
    contextCharacters.computeIfAbsent(provider, ignored -> new LongAdder()).add(context.content().length());
    if (context.degraded()) {
      degradedCalls.computeIfAbsent(provider, ignored -> new LongAdder()).increment();
    }
  }

  /** Returns cumulative process-local provider telemetry for diagnostics and tuning. */
  public Map<String, Map<String, Long>> snapshot() {
    Map<String, Map<String, Long>> result = new LinkedHashMap<>();
    calls.keySet().stream().sorted().forEach(provider -> {
      long callCount = sum(calls, provider);
      long totalLatency = sum(latencyMillis, provider);
      result.put(provider, Map.of(
          "calls", callCount,
          "degradedCalls", sum(degradedCalls, provider),
          "totalLatencyMillis", totalLatency,
          "averageLatencyMillis", callCount == 0L ? 0L : totalLatency / callCount,
          "contextCharacters", sum(contextCharacters, provider)
      ));
    });
    return Map.copyOf(result);
  }

  private long sum(Map<String, LongAdder> source, String provider) {
    LongAdder value = source.get(provider);
    return value == null ? 0L : value.sum();
  }
}
