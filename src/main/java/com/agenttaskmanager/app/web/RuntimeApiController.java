package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.RuntimeStatus;
import com.agenttaskmanager.app.service.RuntimeStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeApiController {

  private final RuntimeStatusService runtimeStatusService;

  public RuntimeApiController(RuntimeStatusService runtimeStatusService) {
    this.runtimeStatusService = runtimeStatusService;
  }

  @GetMapping("/status")
  public RuntimeStatus status() {
    return runtimeStatusService.getRuntimeStatus();
  }
}

