package org.tavall.ai.runtime;

import org.junit.jupiter.api.Test;
import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAgentStagingContractTest {
    private static final Set<String> STAGING_READ = Set.of(
            "repository_staging_discover",
            "repository_staging_inspect_graph",
            "repository_staging_resolve_base",
            "repository_staging_validate"
    );
    private static final Set<String> STAGING_MUTATION = Set.of(
            "repository_staging_ensure",
            "repository_staging_attach",
            "repository_staging_set_state"
    );
    private static final String STAGING_PROMOTION = "repository_staging_prepare_promotion";
    private static final Set<String> ENVIRONMENT_CONTEXT_READ = Set.of(
            "cloud_dev_lane_list",
            "cloud_dev_lane_inspect",
            "cloud_dev_environment_list",
            "cloud_dev_environment_inspect",
            "cloud_dev_environment_components",
            "cloud_dev_environment_validations"
    );

    @Test
    void reconciliationOwnsTopologyMutationWhileReadOnlyAgentsDoNot() {
        TavallAgentRegistry registry = TavallAgentRegistry.load(Thread.currentThread().getContextClassLoader());

        TavallAgent reconciliation = registry.require("reconciliation");
        assertTrue(reconciliation.requiredFunctionNames().containsAll(STAGING_READ));
        assertTrue(reconciliation.requiredFunctionNames().containsAll(STAGING_MUTATION));
        assertTrue(reconciliation.optionalFunctionNames().contains(STAGING_PROMOTION));

        for (String agentId : Set.of("scheduler", "implementation", "review", "e2e", "architecture", "documentation", "builder")) {
            TavallAgent agent = registry.require(agentId);
            assertTrue(agent.requestedFunctionNames().stream().noneMatch(STAGING_MUTATION::contains),
                    () -> agentId + " must not request staging topology mutation functions");
        }
    }

    @Test
    void orchestrationReadsTopologyAndMayCoordinateRootLevelAttachmentWithoutOwningPromotion() {
        TavallAgent orchestration = TavallAgentRegistry.load(Thread.currentThread().getContextClassLoader())
                .require("orchestration");

        assertTrue(orchestration.requiredFunctionNames().containsAll(STAGING_READ));
        assertTrue(orchestration.optionalFunctionNames().contains("repository_staging_ensure"));
        assertTrue(orchestration.optionalFunctionNames().contains("repository_staging_attach"));
        assertFalse(orchestration.requestedFunctionNames().contains("repository_staging_set_state"));
        assertFalse(orchestration.requestedFunctionNames().contains(STAGING_PROMOTION));
    }

    @Test
    void workAgentsRequestTheStagingReadsNeededByTheirAcceptanceBoundary() {
        TavallAgentRegistry registry = TavallAgentRegistry.load(Thread.currentThread().getContextClassLoader());

        assertContains(registry, "implementation", "repository_staging_discover", "repository_staging_resolve_base", "repository_staging_validate");
        assertContains(registry, "review", "repository_staging_discover", "repository_staging_inspect_graph", "repository_staging_validate");
        assertContains(registry, "e2e", "repository_staging_discover", "repository_staging_inspect_graph", "repository_staging_validate");
        assertContains(registry, "architecture", STAGING_READ.toArray(String[]::new));
        assertContains(registry, "documentation", "repository_staging_discover", "repository_staging_inspect_graph", "repository_staging_validate");
        assertContains(registry, "scheduler", "repository_staging_discover", "repository_staging_inspect_graph");

        TavallAgent builder = registry.require("builder");
        assertTrue(builder.optionalFunctionNames().containsAll(STAGING_READ));
    }

    @Test
    void stagingAwareRolesBindGraphEvidenceToCloudLaneAndEnvironmentContext() {
        TavallAgentRegistry registry = TavallAgentRegistry.load(Thread.currentThread().getContextClassLoader());

        for (String agentId : Set.of(
                "architecture", "documentation", "e2e", "implementation", "orchestration",
                "reconciliation", "review", "scheduler"
        )) {
            TavallAgent agent = registry.require(agentId);
            assertTrue(agent.requestedFunctionNames().containsAll(ENVIRONMENT_CONTEXT_READ),
                    () -> agentId + " must bind staging work to lane/environment evidence");
        }

        TavallAgent builder = registry.require("builder");
        assertTrue(builder.optionalFunctionNames().containsAll(ENVIRONMENT_CONTEXT_READ));
        assertTrue(registry.require("reconciliation").requiredFunctionNames()
                .contains("cloud_dev_environment_resolve"));
        assertTrue(registry.require("orchestration").optionalFunctionNames()
                .contains("cloud_dev_environment_resolve"));
    }

    @Test
    void sharedStagingSkillsAreCanonicalAndDoNotDuplicateTheGitWorkflowEverywhere() throws Exception {
        Path root = locateRepositoryRoot();
        Path workflow = root.resolve("plugins/tavall-ai/skills/tavall-staging-pr-workflow/SKILL.md");
        Path reconciliation = root.resolve("plugins/tavall-ai/skills/tavall-staging-reconciliation/SKILL.md");
        Path promotion = root.resolve("plugins/tavall-ai/skills/tavall-staging-promotion/SKILL.md");

        assertTrue(Files.isRegularFile(workflow));
        assertTrue(Files.isRegularFile(reconciliation));
        assertTrue(Files.isRegularFile(promotion));

        String workflowText = Files.readString(workflow);
        String reconciliationText = Files.readString(reconciliation);
        String promotionText = Files.readString(promotion);

        for (String functionName : STAGING_READ) {
            assertTrue(workflowText.contains(functionName));
        }
        for (String functionName : ENVIRONMENT_CONTEXT_READ) {
            assertTrue(workflowText.contains(functionName));
        }
        for (String functionName : STAGING_MUTATION) {
            assertTrue(reconciliationText.contains(functionName));
        }
        assertTrue(promotionText.contains(STAGING_PROMOTION));
        assertTrue(promotionText.contains("does not merge"));
    }

    private static void assertContains(TavallAgentRegistry registry, String agentId, String... names) {
        assertTrue(registry.require(agentId).requestedFunctionNames().containsAll(Set.of(names)),
                () -> agentId + " is missing required staging function requests");
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Could not locate Tavall AI repository root");
        return current;
    }
}
