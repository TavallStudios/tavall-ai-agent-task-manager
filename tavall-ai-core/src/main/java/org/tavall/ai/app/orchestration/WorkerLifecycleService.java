package org.tavall.ai.app.orchestration;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.WorkerSessionCache;
import org.tavall.ai.app.config.OrchestrationProperties;
import org.tavall.ai.app.model.orchestration.DeadWorkerRecord;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerCheckIn;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import org.tavall.ai.app.persistence.postgres.BridgeSessionRepository;
import org.tavall.ai.app.persistence.postgres.JsonSupport;
import org.tavall.ai.app.persistence.postgres.WorkerCheckInRepository;
import org.tavall.ai.app.persistence.postgres.WorkerLeaseRepository;
import org.tavall.ai.app.persistence.postgres.WorkerTaskRepository;
import org.tavall.ai.app.persistence.redis.OrchestrationHotStateStore;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkerLifecycleService {

  private final BridgeSessionRepository bridgeSessionRepository;
  private final WorkerCheckInRepository workerCheckInRepository;
  private final WorkerLeaseRepository workerLeaseRepository;
  private final WorkerTaskRepository workerTaskRepository;
  private final OrchestrationHotStateStore orchestrationHotStateStore;
  private final WorkerSessionCache workerSessionCache;
  private final JsonSupport jsonSupport;
  private final OrchestrationProperties orchestrationProperties;

  public WorkerLifecycleService(
      BridgeSessionRepository bridgeSessionRepository,
      WorkerCheckInRepository workerCheckInRepository,
      WorkerLeaseRepository workerLeaseRepository,
      WorkerTaskRepository workerTaskRepository,
      OrchestrationHotStateStore orchestrationHotStateStore,
      WorkerSessionCache workerSessionCache,
      JsonSupport jsonSupport,
      OrchestrationProperties orchestrationProperties
  ) {
    this.bridgeSessionRepository = bridgeSessionRepository;
    this.workerCheckInRepository = workerCheckInRepository;
    this.workerLeaseRepository = workerLeaseRepository;
    this.workerTaskRepository = workerTaskRepository;
    this.orchestrationHotStateStore = orchestrationHotStateStore;
    this.workerSessionCache = workerSessionCache;
    this.jsonSupport = jsonSupport;
    this.orchestrationProperties = orchestrationProperties;
  }

  public void registerWorker(
      String sessionId,
      String agentId,
      String hostName,
      String clientName,
      String repoPath,
      WorkerTransportKind transportKind
  ) {
    bridgeSessionRepository.upsertBridgeSession(
        sessionId,
        agentId,
        "online",
        hostName,
        clientName,
        repoPath,
        jsonSupport.write(Map.of("transport", transportKind.name(), "workerRole", "worker"))
    );
    workerSessionCache.put(
        sessionId,
        CacheDomain.WORKER,
        CacheType.WORKER_SESSION,
        CacheSource.REDIS,
        Map.of("agentId", agentId, "transport", transportKind.name()),
        Duration.ofMinutes(2).toMillis()
    );
  }

  public void registerCleanupAgent(String sessionId, String agentId, String hostName, String clientName) {
    bridgeSessionRepository.upsertBridgeSession(
        sessionId,
        agentId,
        "online",
        hostName,
        clientName,
        "",
        jsonSupport.write(Map.of("transport", WorkerTransportKind.BRIDGE_SESSION.name(), "workerRole", "cleanup"))
    );
  }

  public WorkerCheckIn submitWorkerCheckIn(
      String workerTaskId,
      String taskId,
      String agentId,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    WorkerCheckIn checkIn = workerCheckInRepository.appendCheckIn(workerTaskId, taskId, agentId, status, summary, details);
    orchestrationHotStateStore.recordWorkerHeartbeat(
        workerTaskId,
        agentId,
        Duration.ofSeconds(orchestrationProperties.getCheckInTimeoutSeconds())
    );
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, status);
    return checkIn;
  }

  public void heartbeatWorker(String workerTaskId, String agentId) {
    workerLeaseRepository.heartbeatLease(workerTaskId, orchestrationProperties.getLeaseDurationSeconds());
    orchestrationHotStateStore.recordWorkerHeartbeat(
        workerTaskId,
        agentId,
        Duration.ofSeconds(orchestrationProperties.getLeaseDurationSeconds())
    );
  }

  public DeadWorkerRecord markWorkerDead(String workerTaskId, String summary) {
    String agentId = String.valueOf(orchestrationHotStateStore.getWorkerStatus(workerTaskId).getOrDefault("agentId", ""));
    workerTaskRepository.updateWorkerTaskStatus(workerTaskId, TaskLifecycleStatus.DEAD, summary);
    workerLeaseRepository.deleteLease(workerTaskId);
    orchestrationHotStateStore.recordWorkerStatus(workerTaskId, TaskLifecycleStatus.DEAD);
    return new DeadWorkerRecord(workerTaskId, agentId, OffsetDateTime.now(), summary);
  }
}

