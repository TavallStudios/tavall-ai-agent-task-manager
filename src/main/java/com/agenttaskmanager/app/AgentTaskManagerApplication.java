package com.agenttaskmanager.app;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import com.agenttaskmanager.app.config.RepoCatalogProperties;
import com.agenttaskmanager.app.config.SecurityProperties;
import com.agenttaskmanager.app.config.TaskRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    SecurityProperties.class,
    TaskRuntimeProperties.class,
    CodexBridgeProperties.class,
    RepoCatalogProperties.class
})
public class AgentTaskManagerApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentTaskManagerApplication.class, args);
  }
}
