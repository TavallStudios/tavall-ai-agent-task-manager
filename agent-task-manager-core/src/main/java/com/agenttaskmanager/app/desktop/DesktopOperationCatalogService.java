package com.agenttaskmanager.app.desktop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DesktopOperationCatalogService {

  public Map<String, Object> catalog() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("groups", List.of(
        group(
            "delegation-and-gate",
            "Delegation and Gate",
            "Codex-native delegation runs and fail-closed approval controls.",
            List.of(
                operation(
                    "startDelegationRun",
                    "Start Delegation Run",
                    "Start canonical delegation-run orchestration.",
                    true,
                    "backend"
                ),
                operation(
                    "completeDelegationRun",
                    "Complete Delegation Run",
                    "Finalize run state and persist summary.",
                    true,
                    "backend"
                ),
                operation(
                    "runHarnessApprovalGate",
                    "Run Approval Gate",
                    "Enforce cleanup, validation, patch scope, and integration tests.",
                    true,
                    "backend"
                ),
                operation(
                    "runJavaLintValidation",
                    "Run Java Lint",
                    "Run Checkstyle, PMD, and Error Prone lint checks.",
                    true,
                    "backend"
                )
            )
        ),
        group(
            "memory",
            "Memory",
            "Thread, semantic, and prior-fix memory operations.",
            List.of(
                operation(
                    "searchRelatedContexts",
                    "Search Related Contexts",
                    "Load related semantic context chunks.",
                    true,
                    "backend"
                ),
                operation(
                    "searchPriorFixes",
                    "Search Prior Fixes",
                    "Retrieve related fix history.",
                    true,
                    "backend"
                ),
                operation(
                    "loadRelatedSemanticContext",
                    "Load Semantic Context",
                    "Hydrate task context from semantic memory.",
                    true,
                    "backend"
                )
            )
        ),
        group(
            "computer-use",
            "Computer Use",
            "Runner registration, remote sessions, capture, and input orchestration.",
            List.of(
                operation(
                    "registerComputerUseRunner",
                    "Register Runner",
                    "Register external automation runners.",
                    true,
                    "backend"
                ),
                operation(
                    "startComputerUseSession",
                    "Start Session",
                    "Start a computer-use session on a selected runner.",
                    true,
                    "backend"
                ),
                operation(
                    "captureComputerUseWindow",
                    "Capture Window",
                    "Capture and optionally persist window/frame artifacts.",
                    true,
                    "backend"
                )
            )
        )
    ));
    return payload;
  }

  private Map<String, Object> group(
      String groupKey,
      String displayName,
      String summary,
      List<Map<String, Object>> operations
  ) {
    Map<String, Object> group = new LinkedHashMap<>();
    group.put("groupKey", groupKey);
    group.put("displayName", displayName);
    group.put("summary", summary);
    group.put("operations", operations);
    return group;
  }

  private Map<String, Object> operation(
      String operationKey,
      String displayName,
      String summary,
      boolean enabled,
      String source
  ) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operationKey", operationKey);
    operation.put("displayName", displayName);
    operation.put("summary", summary);
    operation.put("enabled", enabled);
    operation.put("source", source);
    return operation;
  }
}
