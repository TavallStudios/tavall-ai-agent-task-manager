package com.agenttaskmanager.app.cleanjava;

import com.agenttaskmanager.app.AgentTaskManagerApplication;
import com.agenttaskmanager.app.cli.CliCommandService;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class CleanJavaMcpLauncher {

  private CleanJavaMcpLauncher() {
  }

  public static void main(String[] args) {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(AgentTaskManagerApplication.class)
        .web(WebApplicationType.NONE)
        .properties("app.bridge.enabled=false")
        .properties("app.orchestration.autonomy-enabled=false")
        .properties("app.mcp.tool-groups=clean-java-mcp")
        .run(args);
    int exitCode = context.getBean(CliCommandService.class).execute(List.of("serve-mcp-stdio"));
    context.close();
    System.exit(exitCode);
  }
}
