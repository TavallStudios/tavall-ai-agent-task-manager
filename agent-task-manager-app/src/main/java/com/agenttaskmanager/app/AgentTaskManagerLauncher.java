package com.agenttaskmanager.app;

import com.agenttaskmanager.app.cli.AgentTaskManagerCli;
import java.util.Set;

public final class AgentTaskManagerLauncher {

  private static final Set<String> CLI_COMMANDS = Set.of(
      "validate",
      "scan",
      "patch-check",
      "run-agent",
      "run-workers",
      "run-autonomy-cycle",
      "print-rule-report",
      "example-report",
      "serve-mcp-stdio",
      "remote-mcp-smoke",
      "reindex-knowledge",
      "search-knowledge"
  );

  private AgentTaskManagerLauncher() {
  }

  public static void main(String[] args) {
    if (args.length > 0 && CLI_COMMANDS.contains(args[0])) {
      AgentTaskManagerCli.main(args);
      return;
    }
    StandaloneAgentTaskManagerServer.main(args);
  }
}
