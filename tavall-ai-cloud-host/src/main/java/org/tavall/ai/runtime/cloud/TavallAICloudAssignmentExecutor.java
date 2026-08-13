package org.tavall.ai.runtime.cloud;

/**
 * Execution backend for an already-authorized Cloud assignment.
 *
 * <p>This SPI does not grant process isolation by itself. A Codex backend must be composed with
 * the Cloud-owned process supervisor before it is registered here.</p>
 */
@FunctionalInterface
public interface TavallAICloudAssignmentExecutor {
    TavallAICloudExecutionResult execute(TavallAICloudExecutionContext context) throws Exception;
}
