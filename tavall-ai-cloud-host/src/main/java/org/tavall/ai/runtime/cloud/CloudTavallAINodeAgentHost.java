package org.tavall.ai.runtime.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleRegistry;
import org.tavall.ai.runtime.TavallAINodeAgentHost;
import org.tavall.cloud.ai.broker.CloudAINodeAgentAssignment;
import org.tavall.cloud.ai.broker.CloudAINodeAgentLease;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.UUID;

/** Tavall Cloud local-broker adapter for the Tavall AI Node Agent runtime. */
public final class CloudTavallAINodeAgentHost implements TavallAINodeAgentHost {
    static final int EXECUTION_FAILED = 2;
    static final int LIFECYCLE_COMMIT_FAILED = 3;
    private static final int MAXIMUM_RESULT_BYTES = 512 * 1024;
    private static final int MAXIMUM_ERROR_BYTES = 8 * 1024;

    private final BrokerFactory brokerFactory;
    private final ExecutorResolver executorResolver;
    private final Clock clock;

    public CloudTavallAINodeAgentHost() {
        this(
                socketPath -> new CloudTavallAINodeAgentBrokerClient(socketPath, new ObjectMapper()),
                CloudTavallAINodeAgentHost::loadExecutor,
                Clock.systemUTC()
        );
    }

    CloudTavallAINodeAgentHost(BrokerFactory brokerFactory, ExecutorResolver executorResolver, Clock clock) {
        this.brokerFactory = Objects.requireNonNull(brokerFactory, "brokerFactory");
        this.executorResolver = Objects.requireNonNull(executorResolver, "executorResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public int run(TavallAIAgentRoleRegistry roles, List<String> arguments, PrintStream output) throws Exception {
        TavallAIAgentRoleRegistry safeRoles = Objects.requireNonNull(roles, "roles");
        PrintStream safeOutput = Objects.requireNonNull(output, "output");
        HostArguments parsed = HostArguments.parse(arguments);

        try (CloudTavallAINodeAgentBroker broker = brokerFactory.create(parsed.brokerSocket())) {
            CloudAINodeAgentAssignment assignment = broker.acquire(parsed.leaseId());
            validateAssignment(parsed.leaseId(), assignment);
            TavallAIAgentRole role = safeRoles.require(assignment.execution().roleId());
            Path workspace = requireWorkspace(assignment.lease());
            TavallAICloudAssignmentExecutor executor = executorResolver.resolve();

            long runningVersion = broker.acknowledgeRunning(parsed.leaseId());
            TavallAICloudExecutionResult executionResult;
            try {
                executionResult = Objects.requireNonNull(
                        executor.execute(new TavallAICloudExecutionContext(role, assignment, workspace)),
                        "Tavall AI Cloud executor result"
                );
                validateResult(executionResult);
            } catch (Exception exception) {
                String errorMessage = boundedMessage(exception);
                try {
                    broker.fail(parsed.leaseId(), runningVersion, errorMessage);
                } catch (Exception lifecycleException) {
                    safeOutput.println("Tavall AI execution failed and CONTROL rejected the terminal failure acknowledgement.");
                    return LIFECYCLE_COMMIT_FAILED;
                }
                safeOutput.println("Tavall AI execution failed: " + errorMessage);
                return EXECUTION_FAILED;
            }

            try {
                if (executionResult.successful()) {
                    broker.complete(parsed.leaseId(), runningVersion, executionResult.resultJson());
                    safeOutput.println("Tavall AI completed Cloud lease " + parsed.leaseId() + ".");
                    return 0;
                }
                broker.fail(parsed.leaseId(), runningVersion, executionResult.errorMessage());
                safeOutput.println("Tavall AI execution failed: " + executionResult.errorMessage());
                return EXECUTION_FAILED;
            } catch (Exception lifecycleException) {
                safeOutput.println("Tavall AI execution finished locally but CONTROL rejected the terminal acknowledgement.");
                return LIFECYCLE_COMMIT_FAILED;
            }
        }
    }

    private void validateAssignment(UUID requestedLeaseId, CloudAINodeAgentAssignment assignment) {
        CloudAINodeAgentAssignment safeAssignment = Objects.requireNonNull(assignment, "assignment");
        CloudAINodeAgentLease lease = safeAssignment.lease();
        if (!requestedLeaseId.equals(lease.leaseId())) {
            throw new IllegalStateException("Tavall Cloud AI broker returned a different lease");
        }
        if (lease.isExpired(clock.instant())) {
            throw new IllegalStateException("Tavall Cloud AI lease expired before execution");
        }
    }

    private static Path requireWorkspace(CloudAINodeAgentLease lease) throws Exception {
        Path workspace = lease.workspace().toRealPath();
        if (!Files.isDirectory(workspace)) {
            throw new IllegalStateException("Tavall Cloud AI lease workspace is not a directory");
        }
        return workspace;
    }

    private static void validateResult(TavallAICloudExecutionResult result) {
        requireBounded(result.resultJson(), MAXIMUM_RESULT_BYTES, "Tavall AI result");
        requireBounded(result.errorMessage(), MAXIMUM_ERROR_BYTES, "Tavall AI error message");
    }

    private static void requireBounded(String value, int maximumBytes, String description) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException(description + " exceeds the local broker limit");
        }
    }

    private static String boundedMessage(Exception exception) {
        String message = exception.getMessage();
        String safeMessage = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        byte[] bytes = safeMessage.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAXIMUM_ERROR_BYTES) {
            return safeMessage;
        }
        return new String(bytes, 0, MAXIMUM_ERROR_BYTES, StandardCharsets.UTF_8);
    }

    private static TavallAICloudAssignmentExecutor loadExecutor() {
        List<TavallAICloudAssignmentExecutor> executors = new ArrayList<>();
        ServiceLoader.load(
                TavallAICloudAssignmentExecutor.class,
                Thread.currentThread().getContextClassLoader()
        ).forEach(executors::add);
        if (executors.isEmpty()) {
            throw new IllegalStateException("No Tavall AI Cloud assignment executor is installed");
        }
        if (executors.size() > 1) {
            throw new IllegalStateException("Multiple Tavall AI Cloud assignment executors are installed");
        }
        return executors.getFirst();
    }

    @FunctionalInterface
    interface BrokerFactory {
        CloudTavallAINodeAgentBroker create(Path socketPath) throws Exception;
    }

    @FunctionalInterface
    interface ExecutorResolver {
        TavallAICloudAssignmentExecutor resolve();
    }

    private record HostArguments(Path brokerSocket, UUID leaseId) {
        private static HostArguments parse(List<String> arguments) {
            List<String> safeArguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
            Path brokerSocket = null;
            UUID leaseId = null;
            for (int index = 0; index < safeArguments.size(); index += 2) {
                if (index + 1 >= safeArguments.size()) {
                    throw new IllegalArgumentException("Tavall AI Cloud host arguments must be option/value pairs");
                }
                String option = safeArguments.get(index);
                String value = safeArguments.get(index + 1);
                switch (option) {
                    case "--broker-socket" -> {
                        if (brokerSocket != null) {
                            throw new IllegalArgumentException("--broker-socket may only be supplied once");
                        }
                        Path parsedPath = Path.of(value).normalize();
                        if (!parsedPath.isAbsolute()) {
                            throw new IllegalArgumentException("--broker-socket must be absolute");
                        }
                        brokerSocket = parsedPath;
                    }
                    case "--lease-id" -> {
                        if (leaseId != null) {
                            throw new IllegalArgumentException("--lease-id may only be supplied once");
                        }
                        leaseId = UUID.fromString(value);
                    }
                    default -> throw new IllegalArgumentException("Unknown Tavall AI Cloud host option: " + option);
                }
            }
            if (brokerSocket == null || leaseId == null) {
                throw new IllegalArgumentException("Tavall AI Cloud host requires --broker-socket and --lease-id");
            }
            return new HostArguments(brokerSocket, leaseId);
        }
    }
}
