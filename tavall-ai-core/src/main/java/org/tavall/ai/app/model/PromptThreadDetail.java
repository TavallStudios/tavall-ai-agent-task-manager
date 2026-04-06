package org.tavall.ai.app.model;

import java.util.List;

public record PromptThreadDetail(
    PromptThreadSummary thread,
    List<PromptRequestSummary> requests,
    List<PromptMessage> messages
) {
}

