package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.OperatorSurfaceStatus;
import com.agenttaskmanager.app.model.RuntimeStatus;
import com.agenttaskmanager.app.service.OperatorSurfaceService;
import com.agenttaskmanager.app.service.RuntimeStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeApiController {

  private final RuntimeStatusService runtimeStatusService;
  private final OperatorSurfaceService operatorSurfaceService;

  public RuntimeApiController(
      RuntimeStatusService runtimeStatusService,
      OperatorSurfaceService operatorSurfaceService
  ) {
    this.runtimeStatusService = runtimeStatusService;
    this.operatorSurfaceService = operatorSurfaceService;
  }

  @GetMapping("/status")
  public RuntimeStatus status() {
    return runtimeStatusService.getRuntimeStatus();
  }

  @GetMapping("/access")
  public OperatorSurfaceStatus access() {
    return operatorSurfaceService.loadStatus();
  }
}
