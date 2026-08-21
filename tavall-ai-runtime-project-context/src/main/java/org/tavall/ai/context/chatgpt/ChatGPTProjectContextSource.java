package org.tavall.ai.context.chatgpt;

import org.tavall.ai.context.TavallAIContextItem;
import org.tavall.ai.context.TavallAIProjectContextBundle;
import org.tavall.ai.context.TavallAIProjectContextProjection;
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
        for (TavallAIContextItem item : deduplicated.values()) {
            if (bounded.size() >= safeRequest.maxItems()) break;
            TavallAIContextItem boundedItem = fitItem(
                    snapshot.sourceVersion(),
                    safeRequest,
                    bounded,
                    item
            );
            if (boundedItem != null) bounded.add(boundedItem);
        }

        return bundle(snapshot.sourceVersion(), safeRequest.projectId(), bounded);
    }

    private TavallAIContextItem fitItem(
            String sourceVersion,
            TavallAIProjectContextRequest request,
            List<TavallAIContextItem> accepted,
            TavallAIContextItem item
    ) {
        if (fits(sourceVersion, request.projectId(), accepted, item, request.maxCharacters())) return item;

        TavallAIContextItem emptyContent = item.withContent("");
        if (!fits(sourceVersion, request.projectId(), accepted, emptyContent, request.maxCharacters())) return null;

        int low = 0;
        int high = item.content().length();
        int best = 0;
        while (low <= high) {
            int midpoint = low + (high - low) / 2;
            TavallAIContextItem candidate = item.withContent(item.content().substring(0, midpoint));
            if (fits(sourceVersion, request.projectId(), accepted, candidate, request.maxCharacters())) {
                best = midpoint;
                low = midpoint + 1;
            } else {
                high = midpoint - 1;
            }
        }
        return item.withContent(item.content().substring(0, best));
    }

    private boolean fits(
            String sourceVersion,
            String projectId,
            List<TavallAIContextItem> accepted,
            TavallAIContextItem candidate,
            int maxCharacters
    ) {
        List<TavallAIContextItem> projectedItems = new ArrayList<>(accepted.size() + 1);
        projectedItems.addAll(accepted);
        projectedItems.add(candidate);
        return TavallAIProjectContextProjection.projectedCharacters(
                bundle(sourceVersion, projectId, projectedItems)
        ) <= maxCharacters;
    }

    private TavallAIProjectContextBundle bundle(
            String sourceVersion,
            String projectId,
            List<TavallAIContextItem> items
    ) {
        return new TavallAIProjectContextBundle(
                SOURCE_TYPE,
                projectId,
                sourceVersion,
                List.copyOf(items)
        );
    }
}
