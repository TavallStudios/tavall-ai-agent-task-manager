package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.PromptRequestDetail;
import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.service.PromptRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/prompt-requests")
public class PromptRequestApiController {

  private final PromptRequestService promptRequestService;

  public PromptRequestApiController(PromptRequestService promptRequestService) {
    this.promptRequestService = promptRequestService;
  }

  @GetMapping
  public PromptRequestListResponse listPromptRequests(
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "") String status
  ) {
    List<PromptRequestSummary> items = promptRequestService.list(limit, status);
    return new PromptRequestListResponse(items);
  }

  @GetMapping("/{requestId}")
  public PromptRequestDetail getPromptRequest(@PathVariable String requestId) {
    return promptRequestService.getDetail(requestId);
  }

  @PostMapping
  public PromptRequestSummary createPromptRequest(
      @Valid @RequestBody CreatePromptRequestRequest request,
      Principal principal
  ) {
    return promptRequestService.create(
        request.projectKey(),
        request.repoPath(),
        request.executionMode(),
        request.promptText(),
        principal == null ? "unknown" : principal.getName(),
        request.requestedFrom()
    );
  }

  public record PromptRequestListResponse(List<PromptRequestSummary> items) {
  }

  public record CreatePromptRequestRequest(
      @NotBlank String projectKey,
      @NotBlank String repoPath,
      @NotBlank String executionMode,
      String requestedFrom,
      @NotBlank String promptText
  ) {
  }
}

