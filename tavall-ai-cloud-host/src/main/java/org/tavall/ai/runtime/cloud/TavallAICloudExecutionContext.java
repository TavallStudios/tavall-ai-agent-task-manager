package org.tavall.ai.runtime.cloud;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.cloud.ai.broker.CloudAINodeAgentAssignment;

import java.nio.file.Path;
import java.util.Objects;

/** Fully validated local inputs handed from the Cloud host adapter to an execution backend. */
public record TavallAICloudExecutionContext(
        TavallAIAgentRole role,
        CloudAINodeAgentAssignment assignment,
        Path workspace
) {
    public TavallAICloudExecutionContext {
        role = Objects.requireNonNull(role, "role");
        assignment = Objects.requireNonNull(assignment, "assignment");
        workspace = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
    }
}
