package org.tavall.ai.agent.scheduler;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Distributed control-plane role that chooses the owning Tavall AI session and worker. */
public final class SchedulerAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "scheduler";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Chooses the development worker and top-level Tavall AI session for executable work.",
                TavallAIAgentRoleKind.CONTROL,
                TavallAIAgentRoleInstructions.load(SchedulerAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "ai_list_workers",
                        "ai_list_jobs",
                        "ai_inspect_job",
                        "ai_dispatch_job",
                        "github_list_prs",
                        "github_inspect_pr"
                ),
                Set.of(
                        "ai_cancel_job",
                        "ai_recover_job",
                        "ai_list_sessions",
                        "ai_inspect_session"
                ),
                Set.of(
                        TavallAIAgentRoleCapability.FUNCTION_DISCOVERY,
                        TavallAIAgentRoleCapability.PULL_REQUEST_READ,
                        TavallAIAgentRoleCapability.DISTRIBUTED_SCHEDULING
                ),
                false,
                true
        );
    }
}
