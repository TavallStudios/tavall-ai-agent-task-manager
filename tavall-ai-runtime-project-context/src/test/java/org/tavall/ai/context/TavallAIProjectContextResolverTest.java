package org.tavall.ai.context;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TavallAIProjectContextResolverTest {
    @Test
    void returnsBundleOnlyWhenSourceAndProjectMatchTheAuthorizedRequest() throws Exception {
        TavallAIProjectContextResolver resolver = new TavallAIProjectContextResolver(List.of(
                source("chatgpt-project", "project-novus")
        ));
        TavallAIProjectContextRequest request = request("chatgpt-project", "project-novus");

        TavallAIProjectContextBundle bundle = resolver.resolve(request);

        assertEquals("chatgpt-project", bundle.sourceType());
        assertEquals("project-novus", bundle.projectId());
    }

    @Test
    void rejectsBundleFromAnotherProject() {
        TavallAIProjectContextResolver resolver = new TavallAIProjectContextResolver(List.of(
                source("chatgpt-project", "other-project")
        ));

        assertThrows(IllegalStateException.class,
                () -> resolver.resolve(request("chatgpt-project", "project-novus")));
    }

    @Test
    void rejectsBundleFromAnotherSourceType() {
        TavallAIProjectContextSource source = new TavallAIProjectContextSource() {
            @Override
            public String sourceType() {
                return "chatgpt-project";
            }

            @Override
            public TavallAIProjectContextBundle resolve(TavallAIProjectContextRequest request) {
                return new TavallAIProjectContextBundle("foreign-source", request.projectId(), "v1", List.of());
            }
        };
        TavallAIProjectContextResolver resolver = new TavallAIProjectContextResolver(List.of(source));

        assertThrows(IllegalStateException.class,
                () -> resolver.resolve(request("chatgpt-project", "project-novus")));
    }

    private static TavallAIProjectContextSource source(String sourceType, String returnedProjectId) {
        return new TavallAIProjectContextSource() {
            @Override
            public String sourceType() {
                return sourceType;
            }

            @Override
            public TavallAIProjectContextBundle resolve(TavallAIProjectContextRequest request) {
                return new TavallAIProjectContextBundle(sourceType, returnedProjectId, "v1", List.of());
            }
        };
    }

    private static TavallAIProjectContextRequest request(String sourceType, String projectId) {
        return new TavallAIProjectContextRequest(
                sourceType,
                projectId,
                "current architecture",
                Set.of(TavallAIContextKind.FILE),
                8,
                4_096
        );
    }
}
