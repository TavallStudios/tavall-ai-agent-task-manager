package com.agenttaskmanager.app.cli;

import com.agenttaskmanager.app.AgentTaskManagerApplication;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class AgentTaskManagerCli {

  private AgentTaskManagerCli() {
  }

  public static void main(String[] args) {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(AgentTaskManagerApplication.class)
        .web(WebApplicationType.NONE)
        .properties("app.bridge.enabled=false")
        .properties("app.orchestration.autonomy-enabled=false")
        .run(args);
    int exitCode = context.getBean(CliCommandService.class).execute(List.of(args));
    context.close();
    System.exit(exitCode);
  }
}
