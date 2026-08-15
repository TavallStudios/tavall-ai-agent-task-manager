package org.tavall.agent.web;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;

import java.util.Set;

/** Web product design agent. Model execution and external tool authority stay in the parent runtime. */
public final class WebAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "web";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Designs, implements, compares, and validates web product experiences using durable product-scoped design intelligence.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(WebAgentProvider.class, "ROLE.md"),
                Set.of(
                        "repo_read",
                        "repo_search"
                ),
                Set.of(
                        "repo_write",
                        "git_status",
                        "git_diff",
                        "git_commit_checkpoint",
                        "git_push",
                        "ci_verify",
                        "github_inspect_pr",
                        "github_update_pr",
                        "product_intelligence_read",
                        "product_intelligence_record"
                ),
                Set.of("distributed-execution"),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.REPOSITORY_WRITE,
                        TavallAgentCapability.GIT_CHECKPOINT,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.PULL_REQUEST_WRITE,
                        TavallAgentCapability.LOCAL_CI,
                        TavallAgentCapability.RUNTIME_E2E
                ),
                false,
                false
        );
    }
}
