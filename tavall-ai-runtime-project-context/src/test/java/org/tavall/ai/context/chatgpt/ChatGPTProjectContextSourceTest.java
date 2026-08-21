package org.tavall.ai.context.chatgpt;

import org.junit.jupiter.api.Test;
import org.tavall.ai.context.TavallAIContextItem;
import org.tavall.ai.context.TavallAIContextKind;
import org.tavall.ai.context.TavallAIProjectContextBundle;
import org.tavall.ai.context.TavallAIProjectContextProjection;
import org.tavall.ai.context.TavallAIProjectContextRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatGPTProjectContextSourceTest {
    @Test
    void normalizesDeduplicatesFiltersAndEnforcesItemBudget() throws Exception {
        ChatGPTProjectContextGateway gateway = (projectId, query, kinds, maxItems, maxCharacters) ->
                new ChatGPTProjectContextGateway.ChatGPTProjectContextSnapshot(
                        projectId,
                        "snapshot-42",
                        List.of(
                                item("chat-1", TavallAIContextKind.CHAT, "abcd"),
                                item("chat-1", TavallAIContextKind.CHAT, "duplicate"),
                                item("memory-1", TavallAIContextKind.MEMORY, "ignored"),
                                item("file-1", TavallAIContextKind.FILE, "xyz"),
                                item("file-2", TavallAIContextKind.FILE, "not selected")
                        )
                );

        ChatGPTProjectContextSource source = new ChatGPTProjectContextSource(gateway);
        TavallAIProjectContextBundle bundle = source.resolve(new TavallAIProjectContextRequest(
                ChatGPTProjectContextSource.SOURCE_TYPE,
                "tavall",
                "runtime staging context",
                Set.of(TavallAIContextKind.CHAT, TavallAIContextKind.FILE),
                2,
                10_000
        ));

        assertEquals("chatgpt-project", bundle.sourceType());
        assertEquals("tavall", bundle.projectId());
        assertEquals("snapshot-42", bundle.sourceVersion());
        assertEquals(List.of("chat-1", "file-1"), bundle.items().stream().map(TavallAIContextItem::id).toList());
        assertEquals("abcd", bundle.items().get(0).content());
        assertEquals("xyz", bundle.items().get(1).content());
    }

    @Test
    void completeProjectedAttachmentIsBoundedIncludingMetadataAndFraming() throws Exception {
        ChatGPTProjectContextGateway gateway = (projectId, query, kinds, maxItems, maxCharacters) ->
                new ChatGPTProjectContextGateway.ChatGPTProjectContextSnapshot(
                        projectId,
                        "snapshot-with-long-provenance",
                        List.of(new TavallAIContextItem(
                                "chat-with-long-identity",
                                TavallAIContextKind.CHAT,
                                "title-" + "t".repeat(200),
                                "content-that-would-fit-if-content-alone-were-counted",
                                Map.of("origin", "m".repeat(400))
                        ))
                );

        int maxCharacters = 300;
        TavallAIProjectContextBundle bundle = new ChatGPTProjectContextSource(gateway).resolve(
                new TavallAIProjectContextRequest(
                        ChatGPTProjectContextSource.SOURCE_TYPE,
                        "tavall",
                        "",
                        Set.of(TavallAIContextKind.CHAT),
                        10,
                        maxCharacters
                )
        );

        assertTrue(TavallAIProjectContextProjection.projectedCharacters(bundle) <= maxCharacters);
        assertTrue(bundle.items().isEmpty(), "oversized framing/metadata must not bypass the attachment budget");
    }

    @Test
    void truncatesContentAgainstTheCanonicalProjectionBudget() throws Exception {
        TavallAIContextItem fullItem = item("chat-1", TavallAIContextKind.CHAT, "abcdefghij");
        ChatGPTProjectContextGateway gateway = (projectId, query, kinds, maxItems, maxCharacters) ->
                new ChatGPTProjectContextGateway.ChatGPTProjectContextSnapshot(projectId, "v1", List.of(fullItem));

        TavallAIProjectContextBundle emptyContentBundle = new TavallAIProjectContextBundle(
                ChatGPTProjectContextSource.SOURCE_TYPE,
                "tavall",
                "v1",
                List.of(fullItem.withContent(""))
        );
        int maxCharacters = TavallAIProjectContextProjection.projectedCharacters(emptyContentBundle) + 8;

        TavallAIProjectContextBundle bounded = new ChatGPTProjectContextSource(gateway).resolve(
                new TavallAIProjectContextRequest(
                        ChatGPTProjectContextSource.SOURCE_TYPE,
                        "tavall",
                        "",
                        Set.of(TavallAIContextKind.CHAT),
                        1,
                        maxCharacters
                )
        );

        assertEquals(1, bounded.items().size());
        assertTrue(bounded.items().get(0).content().length() < fullItem.content().length());
        assertTrue(TavallAIProjectContextProjection.projectedCharacters(bounded) <= maxCharacters);
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
