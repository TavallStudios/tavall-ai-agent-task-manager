package org.tavall.ai.app.cli;

import org.tavall.ai.app.AgentTaskManagerApplication;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class AgentTaskManagerCli {

  private AgentTaskManagerCli() {
  }

  public static void main(String[] args) {
    boolean stdio = args.length > 0 && "serve-mcp-stdio".equals(args[0]);
    SpringApplicationBuilder builder = new SpringApplicationBuilder(AgentTaskManagerApplication.class)
        .bannerMode(Banner.Mode.OFF)
        .logStartupInfo(false)
        .web(WebApplicationType.NONE)
        .properties("app.bridge.enabled=false")
        .properties("app.orchestration.autonomy-enabled=false")
        .properties("spring.main.banner-mode=off");
    if (stdio) {
      builder.properties("logging.config=classpath:logback-stdio.xml")
          .properties("logging.level.root=WARN");
    }
    ConfigurableApplicationContext context = builder.run(args);
    int exitCode = context.getBean(CliCommandService.class).execute(List.of(args));
    context.close();
    System.exit(exitCode);
  }
}

