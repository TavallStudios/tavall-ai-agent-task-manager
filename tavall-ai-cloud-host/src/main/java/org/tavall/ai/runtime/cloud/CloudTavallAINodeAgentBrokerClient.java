package org.tavall.ai.runtime.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.cloud.ai.broker.CloudAINodeAgentAssignment;
import org.tavall.cloud.ai.broker.CloudAINodeAgentBrokerProtocol;
import org.tavall.cloud.ai.broker.CloudAINodeAgentBrokerRequest;
import org.tavall.cloud.ai.broker.CloudAINodeAgentBrokerResponse;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Length-prefixed JSON client for the dedicated local Tavall Cloud AI broker socket. */
final class CloudTavallAINodeAgentBrokerClient implements CloudTavallAINodeAgentBroker {
    private final Path socketPath;
    private final ObjectMapper objectMapper;
    private final int maximumFrameBytes;

    CloudTavallAINodeAgentBrokerClient(Path socketPath, ObjectMapper objectMapper) {
        this(socketPath, objectMapper, CloudAINodeAgentBrokerProtocol.DEFAULT_MAXIMUM_FRAME_BYTES);
    }

    CloudTavallAINodeAgentBrokerClient(Path socketPath, ObjectMapper objectMapper, int maximumFrameBytes) {
        Path safeSocketPath = Objects.requireNonNull(socketPath, "socketPath").normalize();
        if (!safeSocketPath.isAbsolute()) {
            throw new IllegalArgumentException("Tavall Cloud AI broker socket path must be absolute");
        }
        if (maximumFrameBytes < 1024) {
            throw new IllegalArgumentException("maximumFrameBytes must be at least 1024");
        }
        this.socketPath = safeSocketPath;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.maximumFrameBytes = maximumFrameBytes;
    }

    @Override
    public CloudAINodeAgentAssignment acquire(UUID leaseId) throws Exception {
        CloudAINodeAgentBrokerResponse response = exchange(CloudAINodeAgentBrokerRequest.acquire(leaseId));
        CloudAINodeAgentAssignment assignment = response.assignment();
        if (assignment == null) {
            throw new IllegalStateException("Tavall Cloud AI broker returned no assignment for an accepted lease");
        }
        return assignment;
    }

    @Override
    public long acknowledgeRunning(UUID leaseId) throws Exception {
        CloudAINodeAgentBrokerResponse response = exchange(
                CloudAINodeAgentBrokerRequest.acknowledgeRunning(leaseId)
        );
        if (response.observedJobVersion() < 0) {
            throw new IllegalStateException("Tavall Cloud AI broker returned no RUNNING job generation");
        }
        return response.observedJobVersion();
    }

    @Override
    public void complete(UUID leaseId, long observedJobVersion, String resultJson) throws Exception {
        exchange(CloudAINodeAgentBrokerRequest.complete(leaseId, observedJobVersion, resultJson));
    }

    @Override
    public void fail(UUID leaseId, long observedJobVersion, String errorMessage) throws Exception {
        exchange(CloudAINodeAgentBrokerRequest.fail(leaseId, observedJobVersion, errorMessage));
    }

    private CloudAINodeAgentBrokerResponse exchange(CloudAINodeAgentBrokerRequest request) throws Exception {
        byte[] requestPayload = objectMapper.writeValueAsBytes(Objects.requireNonNull(request, "request"));
        if (requestPayload.length > maximumFrameBytes) {
            throw new IllegalArgumentException("Tavall Cloud AI broker request exceeds the local frame limit");
        }

        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socketPath));
            ByteBuffer requestFrame = ByteBuffer.allocate(Integer.BYTES + requestPayload.length);
            requestFrame.putInt(requestPayload.length).put(requestPayload).flip();
            writeFully(channel, requestFrame);

            ByteBuffer responseLength = ByteBuffer.allocate(Integer.BYTES);
            readFully(channel, responseLength);
            responseLength.flip();
            int frameLength = responseLength.getInt();
            if (frameLength <= 0 || frameLength > maximumFrameBytes) {
                throw new IllegalStateException("Tavall Cloud AI broker response frame is outside the allowed range");
            }
            ByteBuffer responsePayload = ByteBuffer.allocate(frameLength);
            readFully(channel, responsePayload);
            CloudAINodeAgentBrokerResponse response = objectMapper.readValue(
                    responsePayload.array(),
                    CloudAINodeAgentBrokerResponse.class
            );
            requireAccepted(response);
            return response;
        }
    }

    private static void requireAccepted(CloudAINodeAgentBrokerResponse response) {
        CloudAINodeAgentBrokerResponse safeResponse = Objects.requireNonNull(response, "response");
        if (safeResponse.protocolVersion() != CloudAINodeAgentBrokerProtocol.VERSION) {
            throw new IllegalStateException(
                    "Unsupported Tavall Cloud AI broker protocol version: " + safeResponse.protocolVersion()
            );
        }
        if (!safeResponse.successful()) {
            String message = safeResponse.errorMessage();
            throw new IllegalStateException(
                    message == null || message.isBlank() ? "Tavall Cloud AI broker denied the request" : message
            );
        }
    }

    private static void readFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new EOFException("Tavall Cloud AI broker closed the local socket early");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Tavall Cloud AI broker request was interrupted");
            }
        }
    }

    private static void writeFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Tavall Cloud AI broker request was interrupted");
            }
        }
    }
}
