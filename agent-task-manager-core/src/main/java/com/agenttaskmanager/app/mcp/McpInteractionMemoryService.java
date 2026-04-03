package com.agenttaskmanager.app.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agenttaskmanager.app.model.PromptThreadMemoryLookupResult;
import com.agenttaskmanager.app.persistence.postgres.PromptInteractionRepository;
import com.agenttaskmanager.app.persistence.postgres.PromptMessageRepository;
import com.agenttaskmanager.app.service.PromptThreadMemoryService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class McpInteractionMemoryService {

  private final ObjectMapper objectMapper;
  private final PromptInteractionRepository promptInteractionRepository;
  private final PromptMessageRepository promptMessageRepository;
  private final PromptThreadMemoryService promptThreadMemoryService;
  private final McpInteractionThreadResolver threadResolver;

  public McpInteractionMemoryService(
      ObjectMapper objectMapper,
      PromptInteractionRepository promptInteractionRepository,
      PromptMessageRepository promptMessageRepository,
      PromptThreadMemoryService promptThreadMemoryService,
      McpInteractionThreadResolver threadResolver
  ) {
    this.objectMapper = objectMapper;
    this.promptInteractionRepository = promptInteractionRepository;
    this.promptMessageRepository = promptMessageRepository;
    this.promptThreadMemoryService = promptThreadMemoryService;
    this.threadResolver = threadResolver;
  }

  public SyncToolSpecification wrapToolSpecification(SyncToolSpecification specification) {
    return new SyncToolSpecification(
        specification.tool(),
        (exchange, request) -> {
          McpInteractionThreadContext context = threadResolver.resolveTool(
              exchange,
              request.name(),
              request.arguments(),
              request.meta()
          );
          String requestId = beginInteraction(context);
          PromptThreadMemoryLookupResult lookup = captureLookup(context, requestId);
          try {
            var result = specification.callHandler().apply(
                exchange,
                CallToolRequest.builder()
                    .name(request.name())
                    .arguments(request.arguments())
                    .meta(request.meta())
                    .build()
            );
            completeInteraction(context, requestId, "mcp-tool-result", summarizeSuccess(context, lookup), result);
            return result;
          } catch (RuntimeException exception) {
            failInteraction(context, requestId, "mcp-tool-failure", exception);
            throw exception;
          }
        }
    );
  }

  public SyncPromptSpecification wrapPromptSpecification(SyncPromptSpecification specification) {
    return new SyncPromptSpecification(
        specification.prompt(),
        (exchange, request) -> {
          McpInteractionThreadContext context = threadResolver.resolvePrompt(
              exchange,
              request.name(),
              request.arguments(),
              request.meta()
          );
          String requestId = beginInteraction(context);
          PromptThreadMemoryLookupResult lookup = captureLookup(context, requestId);
          try {
            var result = specification.promptHandler().apply(
                exchange,
                new GetPromptRequest(request.name(), request.arguments(), request.meta())
            );
            completeInteraction(context, requestId, "mcp-prompt-result", summarizeSuccess(context, lookup), result);
            return result;
          } catch (RuntimeException exception) {
            failInteraction(context, requestId, "mcp-prompt-failure", exception);
            throw exception;
          }
        }
    );
  }

  public SyncResourceSpecification wrapResourceSpecification(SyncResourceSpecification specification) {
    return new SyncResourceSpecification(
        specification.resource(),
        (exchange, request) -> {
          McpInteractionThreadContext context = threadResolver.resolveResource(exchange, request.uri(), request.meta());
          String requestId = beginInteraction(context);
          PromptThreadMemoryLookupResult lookup = captureLookup(context, requestId);
          try {
            var result = specification.readHandler().apply(exchange, new ReadResourceRequest(request.uri(), request.meta()));
            completeInteraction(context, requestId, "mcp-resource-result", summarizeSuccess(context, lookup), result);
            return result;
          } catch (RuntimeException exception) {
            failInteraction(context, requestId, "mcp-resource-failure", exception);
            throw exception;
          }
        }
    );
  }

  private String beginInteraction(McpInteractionThreadContext context) {
    String requestId = promptInteractionRepository.startInteraction(
        context.projectKey(),
        context.repoPath(),
        context.threadKey(),
        context.interactionType(),
        context.requestSummary(),
        context.requestedBy(),
        context.requestedFrom(),
        context.sessionId()
    );
    promptMessageRepository.appendPromptMessage(
        requestId,
        null,
        context.interactionType() + "-request",
        context.requestedBy(),
        context.requestSummary()
    );
    captureSemanticMessage(context, requestId, context.interactionType() + "-request", context.requestSummary(), Map.of(
        "sender", context.requestedBy(),
        "messageKind", "accepted",
        "interactionName", context.interactionName(),
        "sessionId", context.sessionId()
    ));
    return requestId;
  }

  private PromptThreadMemoryLookupResult captureLookup(McpInteractionThreadContext context, String requestId) {
    if (context.projectKey().isBlank()) {
      return null;
    }
    PromptThreadMemoryLookupResult lookup = promptThreadMemoryService.lookup(
        context.projectKey(),
        context.threadKey(),
        context.lookupText()
    );
    promptMessageRepository.appendPromptMessage(requestId, null, "mcp-memory-lookup", "qdrant-memory", lookup.summary());
    captureSemanticMessage(context, requestId, "mcp-memory-lookup", lookup.summary(), Map.of(
        "sender", "qdrant-memory",
        "messageKind", "memory-lookup",
        "exactThreadFound", lookup.exactThread() != null,
        "threadContextCount", lookup.threadContexts().size(),
        "projectContextCount", lookup.projectContexts().size(),
        "knowledgeContextCount", lookup.knowledgeContexts().size()
    ));
    return lookup;
  }

  private void completeInteraction(
      McpInteractionThreadContext context,
      String requestId,
      String messageKind,
      String summary,
      Object result
  ) {
    String body = serialize(result);
    promptMessageRepository.appendPromptMessage(requestId, null, messageKind, "agent-task-manager-mcp", body);
    promptInteractionRepository.completeInteraction(requestId, summary);
    captureSemanticMessage(context, requestId, messageKind, body, Map.of(
        "sender", "agent-task-manager-mcp",
        "messageKind", "success",
        "summary", summary
    ));
    captureSnapshot(context);
  }

  private void failInteraction(
      McpInteractionThreadContext context,
      String requestId,
      String messageKind,
      RuntimeException exception
  ) {
    String body = exception.getMessage() == null ? exception.toString() : exception.getMessage();
    promptMessageRepository.appendPromptMessage(requestId, null, messageKind, "agent-task-manager-mcp", body);
    promptInteractionRepository.failInteraction(requestId, body);
    captureSemanticMessage(context, requestId, messageKind, body, Map.of(
        "sender", "agent-task-manager-mcp",
        "messageKind", "failure",
        "failureType", exception.getClass().getName()
    ));
    captureSnapshot(context);
  }

  private void captureSemanticMessage(
      McpInteractionThreadContext context,
      String requestId,
      String kind,
      String body,
      Map<String, Object> payload
  ) {
    if (context.projectKey().isBlank()) {
      return;
    }
    promptThreadMemoryService.capturePromptThreadMessage(
        context.projectKey(),
        requestId,
        context.threadKey(),
        context.repoPath(),
        PromptInteractionRepository.mcpHttpTarget(),
        kind,
        body,
        payload
    );
  }

  private void captureSnapshot(McpInteractionThreadContext context) {
    if (context.projectKey().isBlank()) {
      return;
    }
    promptThreadMemoryService.capturePromptThreadSnapshot(context.projectKey(), context.threadKey());
  }

  private String summarizeSuccess(McpInteractionThreadContext context, PromptThreadMemoryLookupResult lookup) {
    if (lookup == null) {
      return context.interactionName() + " completed via MCP HTTP.";
    }
    return context.interactionName()
        + " completed via MCP HTTP with "
        + (lookup.exactThread() == null ? "thread miss." : "thread hit.");
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      return String.valueOf(value);
    }
  }
}
