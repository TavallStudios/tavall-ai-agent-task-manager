package com.agenttaskmanager.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.repo-catalog")
public class RepoCatalogProperties {

  private List<String> roots = new ArrayList<>(List.of(
      "/srv",
      "/srv/local-pc-root/F:/workspace"
  ));
  private int maxDepth = 2;

  public List<String> getRoots() {
    return roots;
  }

  public void setRoots(List<String> roots) {
    this.roots = roots;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }
}
