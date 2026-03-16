package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.model.PromptThreadSummary;
import com.agenttaskmanager.app.service.PromptThreadService;
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
@RequestMapping("/api/threads")
public class PromptThreadApiController {

  private final PromptThreadService promptThreadService;

  public PromptThreadApiController(PromptThreadService promptThreadService) {
    this.promptThreadService = promptThreadService;
  }

  @GetMapping
  public PromptThreadListResponse listThreads(
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "") String bridgeTarget
  ) {
    return new PromptThreadListResponse(promptThreadService.list(limit, bridgeTarget));
  }

  @GetMapping("/detail")
  public PromptThreadDetail getThread(@RequestParam String threadKey) {
    return promptThreadService.getDetail(threadKey);
  }

  public record PromptThreadListResponse(List<PromptThreadSummary> items) {
  }
}
