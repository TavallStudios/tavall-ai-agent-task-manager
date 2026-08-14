package org.tavall.ai.agent.e2e;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Realistic exact-head development-runtime acceptance role. */
public final class E2EAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "e2e";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Deploys an exact head to an authorized development target and collects realistic acceptance evidence.",
                TavallAIAgentRoleKind.WORK,
                TavallAIAgentRoleInstructions.load(E2EAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "ci_verify",
                        "cloud_deploy_development",
                        "cloud_service_logs",
                        "e2e_run"
                ),
                Set.of(
                        "repo_read",
                        "github_inspect_pr",
                        "cloud_inspect_service",
                        "cloud_service_console"
                ),
                Set.of(
                        TavallAIAgentRoleCapability.FUNCTION_DISCOVERY,
                        TavallAIAgentRoleCapability.REPOSITORY_READ,
                        TavallAIAgentRoleCapability.PULL_REQUEST_READ,
                        TavallAIAgentRoleCapability.LOCAL_CI,
                        TavallAIAgentRoleCapability.RUNTIME_E2E
                ),
                false,
                false
        );
    }
}
