package org.tavall.agent.e2e;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;

import java.util.Set;

/** Realistic exact-head development-runtime acceptance agent. */
public final class E2EAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "e2e";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Deploys an exact staging/feature head to an authorized development target and collects realistic acceptance evidence.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(E2EAgentProvider.class, "ROLE.md"),
                Set.of(
                        "ci_verify", "cloud_deploy_development", "cloud_service_logs", "e2e_run",
                        "repository_staging_discover", "repository_staging_inspect_graph", "repository_staging_validate"
                ),
                Set.of("repo_read", "github_inspect_pr", "cloud_inspect_service", "cloud_service_console"),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.LOCAL_CI,
                        TavallAgentCapability.RUNTIME_E2E
                ),
                false,
                false
        );
    }
}
