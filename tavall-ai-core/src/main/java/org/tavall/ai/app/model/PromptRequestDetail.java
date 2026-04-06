package org.tavall.ai.app.model;

import java.util.List;

public record PromptRequestDetail(
    PromptRequestFull request,
    List<PromptRun> runs,
    List<PromptMessage> messages
) {
}


