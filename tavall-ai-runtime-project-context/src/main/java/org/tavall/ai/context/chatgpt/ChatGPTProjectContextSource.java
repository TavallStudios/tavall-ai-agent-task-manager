package org.tavall.ai.context.chatgpt;

import org.tavall.ai.context.TavallAIContextItem;
import org.tavall.ai.context.TavallAIProjectContextBundle;
import org.tavall.ai.context.TavallAIProjectContextRequest;
import org.tavall.ai.context.TavallAIProjectContextSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** ChatGPT Project source adapter that normalizes host-authorized project context into Tavall AI. */
public final class ChatGPTProjectContextSource implements TavallAIProjectContextSource {
    public static final String SOURCE_TYPE = "chatgpt-project";

    private final ChatGPTProjectContextGateway gateway;

    public ChatGPTProjectContextSource(ChatGPTProjectContextGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public TavallAIProjectContextBundle resolve(TavallAIProjectContextRequest request) throws Exception {
        TavallAIProjectContextRequest safeRequest = Objects.requireNonNull(request, "request");
        if (!SOURCE_TYPE.equals(safeRequest.sourceType())) {
            throw new IllegalArgumentException("ChatGPT project source cannot resolve source type: " + safeRequest.sourceType());
        }

        ChatGPTProjectContextGateway.ChatGPTProjectContextSnapshot snapshot = Objects.requireNonNull(
                gateway.search(
                        safeRequest.projectId(),
                        safeRequest.query(),
                        safeRequest.kinds(),
                        safeRequest.maxItems(),
                        safeRequest.maxCharacters()
                ),
                "ChatGPT project gateway result"
        );
        if (!safeRequest.projectId().equals(snapshot.projectId())) {
            throw new IllegalArgumentException("ChatGPT project gateway returned context for another project");
        }

        Map<String, TavallAIContextItem> deduplicated = new LinkedHashMap<>();
        for (TavallAIContextItem item : snapshot.items()) {
            TavallAIContextItem safeItem = Objects.requireNonNull(item, "context item");
            if (safeRequest.kinds().contains(safeItem.kind())) {
                deduplicated.putIfAbsent(safeItem.id(), safeItem);
            }
        }

        List<TavallAIContextItem> bounded = new ArrayList<>();
        int remainingCharacters = safeRequest.maxCharacters();
        for (TavallAIContextItem item : deduplicated.values()) {
            if (bounded.size() >= safeRequest.maxItems() || remainingCharacters <= 0) break;
            String content = item.content();
            if (content.length() > remainingCharacters) {
                content = content.substring(0, remainingCharacters);
            }
            bounded.add(item.withContent(content));
            remainingCharacters -= content.length();
        }

        return new TavallAIProjectContextBundle(
                SOURCE_TYPE,
                safeRequest.projectId(),
                snapshot.sourceVersion(),
                List.copyOf(bounded)
        );
    }
}
