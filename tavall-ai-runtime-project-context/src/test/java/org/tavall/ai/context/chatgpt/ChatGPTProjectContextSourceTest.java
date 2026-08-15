package org.tavall.ai.context.chatgpt;

import org.junit.jupiter.api.Test;
import org.tavall.ai.context.TavallAIContextItem;
import org.tavall.ai.context.TavallAIContextKind;
import org.tavall.ai.context.TavallAIProjectContextBundle;
import org.tavall.ai.context.TavallAIProjectContextRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatGPTProjectContextSourceTest {
    @Test
    void normalizesDeduplicatesFiltersAndEnforcesBudgets() throws Exception {
        ChatGPTProjectContextGateway gateway = (projectId, query, kinds, maxItems, maxCharacters) ->
                new ChatGPTProjectContextGateway.ChatGPTProjectContextSnapshot(
                        projectId,
                        "snapshot-42",
                        List.of(
                                item("chat-1", TavallAIContextKind.CHAT, "abcd"),
                                item("chat-1", TavallAIContextKind.CHAT, "duplicate"),
                                item("memory-1", TavallAIContextKind.MEMORY, "ignored"),
                                item("file-1", TavallAIContextKind.FILE, "xyz")
                        )
                );

        ChatGPTProjectContextSource source = new ChatGPTProjectContextSource(gateway);
        TavallAIProjectContextBundle bundle = source.resolve(new TavallAIProjectContextRequest(
                ChatGPTProjectContextSource.SOURCE_TYPE,
                "tavall",
                "runtime staging context",
                Set.of(TavallAIContextKind.CHAT, TavallAIContextKind.FILE),
                2,
                5
        ));

        assertEquals("chatgpt-project", bundle.sourceType());
        assertEquals("tavall", bundle.projectId());
        assertEquals("snapshot-42", bundle.sourceVersion());
        assertEquals(List.of("chat-1", "file-1"), bundle.items().stream().map(TavallAIContextItem::id).toList());
        assertEquals("abcd", bundle.items().get(0).content());
        assertEquals("x", bundle.items().get(1).content());
    }

    @Test
    void rejectsGatewayResultForAnotherProject() {
        ChatGPTProjectContextGateway gateway = (projectId, query, kinds, maxItems, maxCharacters) ->
                new ChatGPTProjectContextGateway.ChatGPTProjectContextSnapshot("other", "v1", List.of());

        ChatGPTProjectContextSource source = new ChatGPTProjectContextSource(gateway);
        assertThrows(IllegalArgumentException.class, () -> source.resolve(new TavallAIProjectContextRequest(
                ChatGPTProjectContextSource.SOURCE_TYPE,
                "tavall",
                "",
                Set.of(TavallAIContextKind.CHAT),
                10,
                1000
        )));
    }

    private static TavallAIContextItem item(String id, TavallAIContextKind kind, String content) {
        return new TavallAIContextItem(id, kind, id, content, Map.of("origin", "test"));
    }
}
