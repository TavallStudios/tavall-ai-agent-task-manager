package org.tavall.ai.app.mcp;

import org.tavall.ai.app.dashboard.DashboardSummaryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StateResourceProvider implements McpResourceProvider {

  private static final String JSON_MIME_TYPE = "application/json";

  private final DashboardSummaryService dashboardSummaryService;
  private final McpResultFactory resultFactory;
  private final ObjectMapper objectMapper;

  public StateResourceProvider(
      DashboardSummaryService dashboardSummaryService,
      McpResultFactory resultFactory,
      ObjectMapper objectMapper
  ) {
    this.dashboardSummaryService = dashboardSummaryService;
    this.resultFactory = resultFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<SyncResourceSpecification> resourceSpecifications() {
    return List.of(
        resource("state://dashboard/summary", "state://dashboard/summary", "Live dashboard summary.", this::dashboardSummary),
        resource("state://dashboard/workers", "state://dashboard/workers", "Live worker cards from the dashboard.", this::workerCards),
        resource("state://dashboard/chats", "state://dashboard/chats", "Live chat cards from the dashboard.", this::chatCards),
        resource("state://dashboard/batches", "state://dashboard/batches", "Live task-batch cards from the dashboard.", this::batchCards)
    );
  }

  private SyncResourceSpecification resource(String uri, String name, String description, ResourcePayloadSupplier supplier) {
    Resource resource = Resource.builder()
        .uri(uri)
        .name(name)
        .description(description)
        .mimeType(JSON_MIME_TYPE)
        .build();
    return new SyncResourceSpecification(resource, (exchange, request) -> readResource(uri, supplier));
  }

  private ReadResourceResult readResource(String uri, ResourcePayloadSupplier supplier) {
    return resultFactory.resourceResult(new TextResourceContents(uri, JSON_MIME_TYPE, toJson(supplier.load())));
  }

  private Map<String, Object> dashboardSummary() {
    var summary = dashboardSummaryService.loadDashboardSummary();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("activeChats", summary.activeChats());
    payload.put("deadChats", summary.deadChats());
    payload.put("activeWorkers", summary.activeWorkers());
    payload.put("deadWorkers", summary.deadWorkers());
    payload.put("queuedTasks", summary.queuedTasks());
    payload.put("runningTasks", summary.runningTasks());
    payload.put("failedTasks", summary.failedTasks());
    payload.put("completedTasks", summary.completedTasks());
    payload.put("cleanupReviewsPending", summary.cleanupReviewsPending());
    payload.put("patchRejections", summary.patchRejections());
    payload.put("cacheStats", summary.cacheStats());
    return payload;
  }

  private List<?> workerCards() {
    return dashboardSummaryService.loadDashboardSummary().workers();
  }

  private List<?> chatCards() {
    return dashboardSummaryService.loadDashboardSummary().chats();
  }

  private List<?> batchCards() {
    return dashboardSummaryService.loadDashboardSummary().batches();
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      return "{\"error\":\"" + exception.getMessage().replace("\"", "\\\"") + "\"}";
    }
  }

  @FunctionalInterface
  private interface ResourcePayloadSupplier {
    Object load();
  }
}

