package com.agenttaskmanager.app.web;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

  @GetMapping("/")
  public String index(Model model, Principal principal) {
    model.addAttribute("username", principal == null ? "unknown" : principal.getName());
    return "index";
  }

  @GetMapping("/login")
  public String login() {
    return "login";
  }
}

