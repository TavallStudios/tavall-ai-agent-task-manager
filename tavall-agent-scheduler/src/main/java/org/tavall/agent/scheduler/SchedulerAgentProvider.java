package org.tavall.agent.scheduler;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;

import java.util.Set;

/** Durable workload, worker, and top-level-session placement agent. */
public final class SchedulerAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "scheduler";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Places durable Tavall AI work on an authorized development worker/session; AI provider and node-versus-web call routing belongs to the distributed execution runtime module.",
                TavallAgentKind.CONTROL,
                TavallAgentInstructions.load(SchedulerAgentProvider.class, "ROLE.md"),
                Set.of(
                        "ai_list_workers", "ai_list_jobs", "ai_inspect_job", "ai_dispatch_job",
                        "github_list_prs", "github_inspect_pr",
                        "repository_staging_discover", "repository_staging_inspect_graph",
                        "cloud_dev_lane_list", "cloud_dev_lane_inspect", "cloud_dev_environment_list",
                        "cloud_dev_environment_inspect", "cloud_dev_environment_components",
                        "cloud_dev_environment_validations"
                ),
                Set.of("ai_cancel_job", "ai_recover_job", "ai_list_sessions", "ai_inspect_session"),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.DISTRIBUTED_SCHEDULING
                ),
                false,
                true
        );
    }
}
