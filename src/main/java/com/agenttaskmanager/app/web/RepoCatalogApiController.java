package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.KnownRepo;
import com.agenttaskmanager.app.service.RepoCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos")
public class RepoCatalogApiController {

  private final RepoCatalogService repoCatalogService;

  public RepoCatalogApiController(RepoCatalogService repoCatalogService) {
    this.repoCatalogService = repoCatalogService;
  }

  @GetMapping
  public RepoListResponse listRepos() {
    return new RepoListResponse(repoCatalogService.listRepos());
  }

  public record RepoListResponse(List<KnownRepo> items) {
  }
}
