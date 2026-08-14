package org.tavall.ai.execution.distributed;

import java.util.List;

/**
 * Supplies execution targets already authorized for one request and executes through its owned
 * transport/runtime adapter.
 */
public interface TavallAIExecutionTargetProvider {
    String providerId();

    List<TavallAIExecutionTarget> authorizedTargets(TavallAIExecutionRequest request);

    TavallAIExecutionProviderResult execute(
            TavallAIExecutionTarget target,
            TavallAIExecutionRequest request
    ) throws Exception;
}
