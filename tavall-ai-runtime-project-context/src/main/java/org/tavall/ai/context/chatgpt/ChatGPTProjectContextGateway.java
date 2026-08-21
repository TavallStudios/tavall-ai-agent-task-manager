package org.tavall.ai.context.chatgpt;

import org.tavall.ai.context.TavallAIContextKind;
import org.tavall.ai.context.TavallAIContextItem;

import java.util.List;
import java.util.Set;

/**
 * Host-supplied bridge to an authorized ChatGPT Project context surface.
 *
 * <p>Tavall AI does not scrape ChatGPT or assume an undocumented OpenAI Projects API. The host
 * supplies this gateway from whatever connector/export/session authority is explicitly available.</p>
 */
public interface ChatGPTProjectContextGateway {
    ChatGPTProjectContextSnapshot search(
            String projectId,
            String query,
            Set<TavallAIContextKind> kinds,
            int maxItems,
            int maxCharacters
    ) throws Exception;

    record ChatGPTProjectContextSnapshot(
            String projectId,
            String sourceVersion,
            List<TavallAIContextItem> items
    ) {
        public ChatGPTProjectContextSnapshot {
            if (projectId == null || projectId.isBlank()) {
                throw new IllegalArgumentException("projectId must not be blank");
            }
            projectId = projectId.trim();
            sourceVersion = sourceVersion == null ? "" : sourceVersion.strip();
            items = List.copyOf(items == null ? List.of() : items);
        }
    }
}
