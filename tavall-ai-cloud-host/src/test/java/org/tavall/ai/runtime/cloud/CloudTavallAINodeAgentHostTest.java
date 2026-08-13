package org.tavall.ai.runtime.cloud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;
import org.tavall.ai.agent.role.TavallAIAgentRoleRegistry;
import org.tavall.cloud.ai.broker.CloudAINodeAgentAssignment;
import org.tavall.cloud.ai.broker.CloudAINodeAgentExecutionSpec;
import org.tavall.cloud.ai.broker.CloudAINodeAgentLease;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudTavallAINodeAgentHostTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acknowledgesRunningOnlyAfterRoleWorkspaceAndExecutorAreReady() throws Exception {
        UUID leaseId = UUID.randomUUID();
        Path workspace = temporaryDirectory.resolve("workspace");
        java.nio.file.Files.createDirectories(workspace);
        FakeBroker broker = new FakeBroker(assignment(leaseId, workspace));
        CloudTavallAINodeAgentHost host = new CloudTavallAINodeAgentHost(
                ignored -> broker,
                () -> context -> TavallAICloudExecutionResult.completed("{\"message\":\"done\"}"),
                Clock.fixed(Instant.parse("2026-08-12T20:00:00Z"), ZoneOffset.UTC)
        );

        int exitCode = host.run(
                roles(),
                List.of("--broker-socket", temporaryDirectory.resolve("broker.sock").toString(), "--lease-id", leaseId.toString()),
                new PrintStream(new ByteArrayOutputStream())
        );

        assertEquals(0, exitCode);
        assertTrue(broker.runningAcknowledged);
        assertEquals(12, broker.completedVersion);
        assertFalse(broker.failed);
    }

    @Test
    void missingExecutorFailsBeforeRunningAcknowledgement() throws Exception {
        UUID leaseId = UUID.randomUUID();
        Path workspace = temporaryDirectory.resolve("workspace");
        java.nio.file.Files.createDirectories(workspace);
        FakeBroker broker = new FakeBroker(assignment(leaseId, workspace));
        CloudTavallAINodeAgentHost host = new CloudTavallAINodeAgentHost(
                ignored -> broker,
                () -> { throw new IllegalStateException("No Tavall AI Cloud assignment executor is installed"); },
                Clock.fixed(Instant.parse("2026-08-12T20:00:00Z"), ZoneOffset.UTC)
        );

        assertThrows(IllegalStateException.class, () -> host.run(
                roles(),
                List.of("--broker-socket", temporaryDirectory.resolve("broker.sock").toString(), "--lease-id", leaseId.toString()),
                new PrintStream(new ByteArrayOutputStream())
        ));
        assertFalse(broker.runningAcknowledged);
    }

    private CloudAINodeAgentAssignment assignment(UUID leaseId, Path workspace) {
        return new CloudAINodeAgentAssignment(
                new CloudAINodeAgentLease(
                        leaseId,
                        UUID.randomUUID(),
                        11,
                        "dev-main",
                        5,
                        workspace.toAbsolutePath().toString(),
                        Instant.parse("2026-08-12T21:00:00Z").toEpochMilli()
                ),
                new CloudAINodeAgentExecutionSpec(
                        "implementation",
                        "Implement a bounded change",
                        0,
                        Map.of(),
                        60_000,
                        8,
                        1
                )
        );
    }

    private static TavallAIAgentRoleRegistry roles() {
        TavallAIAgentRole role = new TavallAIAgentRole(
                "implementation",
                "Implementation role",
                TavallAIAgentRoleKind.IMPLEMENTATION,
                "Implement the authorized task.",
                Set.of(),
                Set.of(),
                Set.of(),
                false,
                false
        );
        TavallAIAgentRoleProvider provider = () -> role;
        return new TavallAIAgentRoleRegistry(List.of(provider));
    }

    private static final class FakeBroker implements CloudTavallAINodeAgentBroker {
        private final CloudAINodeAgentAssignment assignment;
        private boolean runningAcknowledged;
        private long completedVersion = -1;
        private boolean failed;

        private FakeBroker(CloudAINodeAgentAssignment assignment) {
            this.assignment = assignment;
        }

        @Override
        public CloudAINodeAgentAssignment acquire(UUID leaseId) {
            assertEquals(assignment.lease().leaseId(), leaseId);
            return assignment;
        }

        @Override
        public long acknowledgeRunning(UUID leaseId) {
            runningAcknowledged = true;
            return 12;
        }

        @Override
        public void complete(UUID leaseId, long observedJobVersion, String resultJson) {
            completedVersion = observedJobVersion;
        }

        @Override
        public void fail(UUID leaseId, long observedJobVersion, String errorMessage) {
            failed = true;
        }
    }
}
