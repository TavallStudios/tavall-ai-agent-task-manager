package org.tavall.ai.app.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import org.springframework.stereotype.Component;
import org.tavall.ai.app.memory.MemoryRuntimeService;
import org.tavall.ai.app.memory.MemoryTurnHandle;
import org.tavall.ai.app.persistence.postgres.PromptInteractionRepository;
import org.tavall.ai.app.persistence.postgres.PromptMessageRepository;

@Component
public class McpInteractionMemoryService {

  private static final int MAX_PERSISTED_MESSAGE_CHARS = 2000;

  private final ObjectMapper objectMapper;
  private final PromptInteractionRepository promptInteractionRepository;
  private final PromptMessageRepository promptMessageRepository;
  private final McpInteractionThreadResolver threadResolver;
  private final MemoryRuntimeService memoryRuntimeService;

  public McpInteractionMemoryService(
      ObjectMapper objectMapper,
      PromptInteractionRepository promptInteractionRepository,
      PromptMessageRepository promptMessageRepository,
      McpInteractionThreadResolver threadResolver,
      MemoryRuntimeService memoryRuntimeService
  ) {
    this.objectMapper = objectMapper;
    this.promptInteractionRepository = promptInteractionRepository;
    this.promptMessageRepository = promptMessageRepository;
    this.threadResolver = threadResolver;
    this.memoryRuntimeService = memoryRuntimeService;
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
          MemoryTurnHandle turnHandle = beginTurn(context, requestId);
          try {
            var result = specification.callHandler().apply(
                exchange,
                CallToolRequest.builder()
                    .name(request.name())
                    .arguments(request.arguments())
                    .meta(request.meta())
                    .build()
            );
            completeInteraction(context, requestId, turnHandle, "mcp-tool-result", summarizeSuccess(context, turnHandle), result);
            return result;
          } catch (RuntimeException exception) {
            failInteraction(context, requestId, turnHandle, "mcp-tool-failure", exception);
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
          MemoryTurnHandle turnHandle = beginTurn(context, requestId);
          try {
            var result = specification.promptHandler().apply(
                exchange,
                new GetPromptRequest(request.name(), request.arguments(), request.meta())
            );
            completeInteraction(context, requestId, turnHandle, "mcp-prompt-result", summarizeSuccess(context, turnHandle), result);
            return result;
          } catch (RuntimeException exception) {
            failInteraction(context, requestId, turnHandle, "mcp-prompt-failure", exception);
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
          MemoryTurnHandle turnHandle = beginTurn(context, requestId);
          try {
            var result = specification.readHandler().apply(exchange, new ReadResourceRequest(request.uri(), request.meta()));
            completeInteraction(context, requestId, turnHandle, "mcp-resource-result", summarizeSuccess(context, turnHandle), result);
            return result;
          } catch (RuntimeException exception) {
            failInteraction(context, requestId, turnHandle, "mcp-resource-failure", exception);
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
        bounded(context.requestSummary())
    );
    return requestId;
  }

  private MemoryTurnHandle beginTurn(McpInteractionThreadContext context, String requestId) {
    if (context.projectKey().isBlank()) {
      return null;
    }
    try {
      MemoryTurnHandle turnHandle = memoryRuntimeService.beginTurn(
          requestId,
          context.projectKey(),
          context.threadKey(),
          context.sessionId(),
          context.requestedBy(),
          context.requestedFrom(),
          context.repoPath(),
          context.requestSummary(),
          context.lookupText(),
          context.meta()
      );
      promptMessageRepository.appendPromptMessage(
          requestId,
          null,
          "mcp-memory-lookup",
          "memory-runtime",
          bounded(turnHandle.hydration().summary())
      );
      return turnHandle;
    } catch (RuntimeException exception) {
      promptMessageRepository.appendPromptMessage(
          requestId,
          null,
          "mcp-memory-lookup-failure",
          "memory-runtime",
          bounded(exception.getMessage() == null ? exception.toString() : exception.getMessage())
      );
      return null;
    }
  }

  private void completeInteraction(
      McpInteractionThreadContext context,
      String requestId,
      MemoryTurnHandle turnHandle,
      String messageKind,
      String summary,
      Object result
  ) {
    String body = bounded(serialize(result));
    promptMessageRepository.appendPromptMessage(requestId, null, messageKind, "tavall-ai-mcp", body);
    promptInteractionRepository.completeInteraction(requestId, summary);
    if (turnHandle != null) {
      memoryRuntimeService.completeTurn(turnHandle, body, false);
    }
  }

  private void failInteraction(
      McpInteractionThreadContext context,
      String requestId,
      MemoryTurnHandle turnHandle,
      String messageKind,
      RuntimeException exception
  ) {
    String body = bounded(exception.getMessage() == null ? exception.toString() : exception.getMessage());
    promptMessageRepository.appendPromptMessage(requestId, null, messageKind, "tavall-ai-mcp", body);
    promptInteractionRepository.failInteraction(requestId, body);
    if (turnHandle != null) {
      memoryRuntimeService.completeTurn(turnHandle, body, true);
    }
  }

  private String summarizeSuccess(McpInteractionThreadContext context, MemoryTurnHandle turnHandle) {
    if (turnHandle == null) {
      return context.interactionName() + " completed via MCP HTTP.";
    }
    return context.interactionName()
        + " completed via MCP HTTP with "
        + turnHandle.hydration().exactRecords().size()
        + " exact memory records.";
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      return String.valueOf(value);
    }
  }

  private String bounded(String value) {
    String normalized = value == null ? "" : value.strip();
    if (normalized.length() <= MAX_PERSISTED_MESSAGE_CHARS) {
      return normalized;
    }
    return normalized.substring(0, MAX_PERSISTED_MESSAGE_CHARS - 3) + "...";
  }
}
