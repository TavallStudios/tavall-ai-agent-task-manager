package org.tavall.ai.app.cli;

import org.tavall.ai.app.dashboard.DashboardSummaryService;
import org.tavall.ai.app.knowledge.KnowledgeIndexService;
import org.tavall.ai.app.mcp.McpCatalog;
import org.tavall.ai.app.model.orchestration.AutonomousCycleReport;
import org.tavall.ai.app.model.orchestration.TaskAssignment;
import org.tavall.ai.app.model.orchestration.WorkerExecutionRequest;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import org.tavall.ai.app.retrieval.ProjectSemanticIndexService;
import org.tavall.ai.app.runtime.ServerRuntimeLock;
import org.tavall.ai.app.model.validation.ValidationReport;
import org.tavall.ai.app.orchestration.AutonomousCycleService;
import org.tavall.ai.app.orchestration.LocalCodexWorkerTransport;
import org.tavall.ai.app.orchestration.OverseerOrchestrationService;
import org.tavall.ai.app.validation.ValidationPipelineService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.springframework.stereotype.Service;

@Service
public class CliCommandService {

  private final AutonomousCycleService autonomousCycleService;
  private final DashboardSummaryService dashboardSummaryService;
  private final KnowledgeIndexService knowledgeIndexService;
  private final LocalCodexWorkerTransport localCodexWorkerTransport;
  private final McpCatalog mcpCatalog;
  private final McpJsonMapper mcpJsonMapper;
  private final ObjectMapper objectMapper;
  private final OverseerOrchestrationService overseerOrchestrationService;
  private final ProjectSemanticIndexService projectSemanticIndexService;
  private final RemoteMcpSmokeService remoteMcpSmokeService;
  private final ValidationPipelineService validationPipelineService;

  public CliCommandService(
      AutonomousCycleService autonomousCycleService,
      DashboardSummaryService dashboardSummaryService,
      KnowledgeIndexService knowledgeIndexService,
      LocalCodexWorkerTransport localCodexWorkerTransport,
      McpCatalog mcpCatalog,
      McpJsonMapper mcpJsonMapper,
      ObjectMapper objectMapper,
      OverseerOrchestrationService overseerOrchestrationService,
      ProjectSemanticIndexService projectSemanticIndexService,
      RemoteMcpSmokeService remoteMcpSmokeService,
      ValidationPipelineService validationPipelineService
  ) {
    this.autonomousCycleService = autonomousCycleService;
    this.dashboardSummaryService = dashboardSummaryService;
    this.knowledgeIndexService = knowledgeIndexService;
    this.localCodexWorkerTransport = localCodexWorkerTransport;
    this.mcpCatalog = mcpCatalog;
    this.mcpJsonMapper = mcpJsonMapper;
    this.objectMapper = objectMapper;
    this.overseerOrchestrationService = overseerOrchestrationService;
    this.projectSemanticIndexService = projectSemanticIndexService;
    this.remoteMcpSmokeService = remoteMcpSmokeService;
    this.validationPipelineService = validationPipelineService;
  }

  public int execute(List<String> args) {
    if (args.isEmpty()) {
      print("Commands: validate, scan, patch-check, run-agent, run-workers, run-autonomy-cycle, print-rule-report, example-report, serve-mcp-stdio, remote-mcp-smoke, reindex-knowledge, reindex-codebases, search-knowledge");
      return 0;
    }

    return switch (args.getFirst()) {
      case "validate" -> validate(args);
      case "scan" -> scan();
      case "patch-check" -> patchCheck(args);
      case "run-agent" -> runAgent(args);
      case "run-workers" -> runWorkers(args);
      case "run-autonomy-cycle" -> runAutonomyCycle(args);
      case "print-rule-report" -> printRuleReport(args);
      case "example-report" -> exampleReport();
      case "serve-mcp-stdio" -> serveMcpStdio();
      case "remote-mcp-smoke" -> remoteMcpSmoke(args);
      case "reindex-knowledge" -> reindexKnowledge();
      case "reindex-codebases" -> reindexCodebases();
      case "search-knowledge" -> searchKnowledge(args);
      default -> {
        print("Unknown command: " + args.getFirst());
        yield 1;
      }
    };
  }

  private int validate(List<String> args) {
    Path repoPath = args.size() > 1 ? Path.of(args.get(1)) : Path.of(".");
    ValidationReport report = validationPipelineService.runValidationPipeline("cli-validate", "cli-worker", repoPath.toAbsolutePath());
    printJson(report);
    return "passed".equals(report.status()) ? 0 : 1;
  }

  private int scan() {
    printJson(dashboardSummaryService.loadDashboardSummary());
    return 0;
  }

  private int patchCheck(List<String> args) {
    if (args.size() < 2) {
      print("patch-check requires a diff file path.");
      return 1;
    }
    try {
      String diff = Files.readString(Path.of(args.get(1)), StandardCharsets.UTF_8);
      printJson(new PatchCheckResponse(validationPipelineService.validatePatchScope(diff)));
      return 0;
    } catch (Exception exception) {
      print(exception.getMessage());
      return 1;
    }
  }

  private int runAgent(List<String> args) {
    if (args.size() < 3) {
      print("run-agent requires <taskId> <repoPath> [agentId]");
      return 1;
    }
    String taskId = args.get(1);
    Path repoPath = Path.of(args.get(2)).toAbsolutePath();
    String agentId = args.size() > 3 ? args.get(3) : "worker@" + UUID.randomUUID();
    String sessionId = "session-" + UUID.randomUUID();
    TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
        taskId,
        agentId,
        WorkerTransportKind.LOCAL_CODEX_EXEC,
        sessionId
    );
    if (assignment == null) {
      print("No queued worker task is available for " + taskId);
      return 0;
    }
    printJson(localCodexWorkerTransport.executeWorkerTask(
        new WorkerExecutionRequest(taskId, assignment.workerTaskId(), agentId, sessionId, repoPath)
    ));
    return 0;
  }

  private int runWorkers(List<String> args) {
    if (args.size() < 3) {
      print("run-workers requires <taskId> <repoPath> [agentPrefix]");
      return 1;
    }
    String taskId = args.get(1);
    Path repoPath = Path.of(args.get(2)).toAbsolutePath();
    String agentPrefix = args.size() > 3 ? args.get(3) : "worker";
    int counter = 0;
    while (true) {
      String agentId = agentPrefix + "-" + counter++;
      String sessionId = "session-" + UUID.randomUUID();
      TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
          taskId,
          agentId,
          WorkerTransportKind.LOCAL_CODEX_EXEC,
          sessionId
      );
      if (assignment == null) {
        break;
      }
      printJson(localCodexWorkerTransport.executeWorkerTask(
          new WorkerExecutionRequest(taskId, assignment.workerTaskId(), agentId, sessionId, repoPath)
      ));
    }
    return 0;
  }

  private int runAutonomyCycle(List<String> args) {
    Path repoPath = args.size() > 1 ? Path.of(args.get(1)).toAbsolutePath() : Path.of(".").toAbsolutePath();
    AutonomousCycleReport report = autonomousCycleService.runCycle(repoPath);
    printJson(report);
    return report.failedBatchIds().isEmpty() ? 0 : 1;
  }

  private int printRuleReport(List<String> args) {
    Path repoPath = args.size() > 1 ? Path.of(args.get(1)).toAbsolutePath() : Path.of(".").toAbsolutePath();
    ValidationReport report = validationPipelineService.runValidationPipeline("cli-rules", "cli-rules", repoPath);
    report.violations().forEach(this::printJson);
    return report.violations().isEmpty() ? 0 : 1;
  }

  private int exampleReport() {
    Path repoPath = Path.of(".").toAbsolutePath();
    ValidationReport report = validationPipelineService.runValidationPipeline("cli-examples", "cli-examples", repoPath);
    printJson(report);
    return report.violations().isEmpty() ? 0 : 1;
  }

  private int serveMcpStdio() {
    try (ServerRuntimeLock runtimeLock = ServerRuntimeLock.acquire("mcp-stdio")) {
      if (runtimeLock == null) {
        printError("Another Tavall AI server is already running. Stop it before starting stdio.");
        return 1;
      }
      StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(mcpJsonMapper);
      McpServer.sync(transportProvider)
          .serverInfo("AgentTaskManager MCP CLI", "0.1.0")
          .instructions("Use the task runtime, validation, cleanup, and semantic retrieval tools.")
          .jsonMapper(mcpJsonMapper)
          .tools(mcpCatalog.toolSpecifications())
          .resources(mcpCatalog.resourceSpecifications())
          .prompts(mcpCatalog.promptSpecifications())
          .build();
      printError("Serving MCP over stdio.");
      try {
        new CountDownLatch(1).await();
        return 0;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return 1;
      }
    }
  }

  private int remoteMcpSmoke(List<String> args) {
    try {
      String baseUrl = args.size() > 1 ? args.get(1) : null;
      String username = args.size() > 2 ? args.get(2) : null;
      String password = args.size() > 3 ? args.get(3) : null;

      RemoteMcpSmokeResult result = baseUrl == null
          ? remoteMcpSmokeService.runSmoke()
          : remoteMcpSmokeService.runSmoke(
              baseUrl,
              "/mcp",
              username == null ? "agent" : username,
              password == null ? "" : password
          );

      printJson(result);
      return 0;
    } catch (Exception exception) {
      print(exception.getMessage());
      return 1;
    }
  }

  private int reindexKnowledge() {
    printJson(knowledgeIndexService.reindex());
    return 0;
  }

  private int reindexCodebases() {
    printJson(projectSemanticIndexService.reindexConfiguredRepos());
    return 0;
  }

  private int searchKnowledge(List<String> args) {
    if (args.size() < 2) {
      print("search-knowledge requires <queryText> [limit]");
      return 1;
    }
    int limit = args.size() > 2 ? Integer.parseInt(args.get(2)) : 5;
    printJson(knowledgeIndexService.search(args.get(1), limit));
    return 0;
  }

  private void printJson(Object payload) {
    try {
      print(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    } catch (JsonProcessingException exception) {
      print(String.valueOf(payload));
    }
  }

  private void print(String value) {
    System.out.println(value);
  }

  private void printError(String value) {
    System.err.println(value);
  }

  private record PatchCheckResponse(boolean allowed) {
  }
}

