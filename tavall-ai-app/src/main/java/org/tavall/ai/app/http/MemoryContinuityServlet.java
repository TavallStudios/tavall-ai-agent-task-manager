package org.tavall.ai.app.http;

import org.tavall.ai.app.memory.MemoryContinuityService;
import org.tavall.ai.app.memory.MemoryIdentity;
import org.tavall.ai.app.memory.MemoryRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public class MemoryContinuityServlet extends HttpServlet {

  private final MemoryContinuityService continuityService;
  private final MemoryRetrievalService retrievalService;
  private final ObjectMapper objectMapper;

  public MemoryContinuityServlet(
      MemoryContinuityService continuityService,
      MemoryRetrievalService retrievalService,
      ObjectMapper objectMapper
  ) {
    this.continuityService = continuityService;
    this.retrievalService = retrievalService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    MemoryIdentity identity = retrievalService.resolveIdentity(
        request.getParameter("projectKey"),
        request.getParameter("threadKey"),
        request.getHeader("Mcp-Session-Id"),
        request.getRemoteUser(),
        "mcp-http",
        request.getParameter("repoPath"),
        Map.of(
            "projectKey", value(request.getParameter("projectKey")),
            "threadKey", value(request.getParameter("threadKey")),
            "chatId", value(request.getParameter("chatId"))
        )
    );
    Object payload = request.getRequestURI().endsWith("/search")
        ? retrievalService.lookup(
            identity.projectId(),
            identity.threadKey(),
            identity.sessionId(),
            identity.requestedBy(),
            identity.requestedFrom(),
            identity.repoPath(),
            value(request.getParameter("query")),
            Map.of(
                "projectKey", identity.projectId(),
                "threadKey", identity.threadKey(),
                "chatId", identity.chatId()
            )
        )
        : continuityService.bootstrap(identity).<Object>map(snapshot -> snapshot).orElse(Map.of(
            "summary", "No continuity snapshot exists for the requested identity.",
            "identity", identity
        ));
    objectMapper.writeValue(response.getWriter(), payload);
  }

  private String value(String value) {
    return value == null ? "" : value.strip();
  }
}

