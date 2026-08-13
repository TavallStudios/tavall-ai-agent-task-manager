package org.tavall.ai.runtime.cloud;

import org.tavall.cloud.ai.broker.CloudAINodeAgentAssignment;

import java.util.UUID;

/** Narrow local lifecycle surface presented by the Tavall Cloud node-agent broker. */
interface CloudTavallAINodeAgentBroker extends AutoCloseable {
    CloudAINodeAgentAssignment acquire(UUID leaseId) throws Exception;

    long acknowledgeRunning(UUID leaseId) throws Exception;

    void complete(UUID leaseId, long observedJobVersion, String resultJson) throws Exception;

    void fail(UUID leaseId, long observedJobVersion, String errorMessage) throws Exception;

    @Override
    default void close() throws Exception {
    }
}
