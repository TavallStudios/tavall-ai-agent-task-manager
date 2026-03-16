# EXAMPLES

## Good Dependency Access

```java
public interface ArtifactDependencyAccess {

    default ArtifactRecord writeArtifact(
        String taskId,
        String workerTaskId,
        String artifactKind,
        String summary,
        String body,
        Map<String, Object> metadata
    ) {
        return ServiceLoaders.artifactService().writeArtifact(
            taskId,
            workerTaskId,
            artifactKind,
            summary,
            body,
            metadata
        );
    }
}
```

## Bad Raw Getter Surface

```java
public interface ArtifactDependencyAccess {

    default ArtifactService getArtifactService() {
        return ServiceLoaders.artifactService();
    }
}
```

Reason:

- leaks plumbing into business code
- defeats direct action exposure

## Good Cleanup Flow

```text
worker diff -> artifact store -> cleanup review -> validation -> overseer decision -> patch outcome
```

## Bad Cleanup Flow

```text
worker says "done" -> patch applied with no diff review or validation
```

## Good CLI Usage

```bash
java -jar agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar validate /srv/AgentTaskManager
java -jar agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar run-workers tb_example /srv/AgentTaskManager worker
java -jar agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar serve-mcp-stdio
```
