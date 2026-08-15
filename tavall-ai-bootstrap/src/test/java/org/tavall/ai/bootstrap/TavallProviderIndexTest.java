package org.tavall.ai.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TavallProviderIndexTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsProviderTypesWithoutConstructingThem() throws Exception {
        Path root = tempDirectory.resolve("one");
        writeIndex(root, "# provider\n\n" + IndexedProvider.class.getName() + "\n");

        try (URLClassLoader loader = loader(root)) {
            List<Class<? extends TavallAgentProvider>> providers = TavallProviderIndex.load(
                    loader,
                    TavallProviderIndex.AGENT_PROVIDER_RESOURCE,
                    TavallAgentProvider.class
            );

            assertEquals(List.of(IndexedProvider.class), providers);
            assertEquals(0, IndexedProvider.constructions);
        }
    }

    @Test
    void rejectsDuplicateProviderMembershipAcrossResources() throws Exception {
        Path first = tempDirectory.resolve("first");
        Path second = tempDirectory.resolve("second");
        writeIndex(first, IndexedProvider.class.getName() + "\n");
        writeIndex(second, IndexedProvider.class.getName() + "\n");

        try (URLClassLoader loader = loader(first, second)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> TavallProviderIndex.load(
                            loader,
                            TavallProviderIndex.AGENT_PROVIDER_RESOURCE,
                            TavallAgentProvider.class
                    )
            );
        }
    }

    @Test
    void rejectsIndexedTypesOutsideTheProviderContract() throws Exception {
        Path root = tempDirectory.resolve("invalid");
        writeIndex(root, String.class.getName() + "\n");

        try (URLClassLoader loader = loader(root)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> TavallProviderIndex.load(
                            loader,
                            TavallProviderIndex.AGENT_PROVIDER_RESOURCE,
                            TavallAgentProvider.class
                    )
            );
        }
    }

    private void writeIndex(Path root, String content) throws Exception {
        Path index = root.resolve(TavallProviderIndex.AGENT_PROVIDER_RESOURCE);
        Files.createDirectories(index.getParent());
        Files.writeString(index, content);
    }

    private URLClassLoader loader(Path... roots) throws Exception {
        URL[] urls = new URL[roots.length];
        for (int index = 0; index < roots.length; index++) {
            urls[index] = roots[index].toUri().toURL();
        }
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    public static final class IndexedProvider implements TavallAgentProvider {
        private static int constructions;

        public IndexedProvider() {
            constructions++;
        }

        @Override
        public TavallAgent agent() {
            return new TavallAgent(
                    "indexed-test",
                    "Index test agent.",
                    TavallAgentKind.WORK,
                    "Test provider indexing.",
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    false,
                    false
            );
        }
    }
}
