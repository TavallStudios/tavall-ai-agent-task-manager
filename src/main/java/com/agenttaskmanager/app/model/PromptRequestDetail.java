package com.agenttaskmanager.app.model;

import java.util.List;

public record PromptRequestDetail(
    PromptRequestFull request,
    List<PromptRun> runs,
    List<PromptMessage> messages
) {
}

