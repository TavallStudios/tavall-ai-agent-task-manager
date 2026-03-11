package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.BridgeSessionSummary;
import com.agenttaskmanager.app.service.PromptExecutionStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/bridge/sessions")
public class BridgeSessionApiController {

  private final PromptExecutionStore executionStore;

  public BridgeSessionApiController(PromptExecutionStore executionStore) {
    this.executionStore = executionStore;
  }

  @GetMapping
  public BridgeSessionListResponse listSessions(
      @RequestParam(defaultValue = "12") @Min(1) @Max(100) int limit
  ) {
    return new BridgeSessionListResponse(executionStore.listBridgeSessions(limit));
  }

  public record BridgeSessionListResponse(List<BridgeSessionSummary> items) {
  }
}
