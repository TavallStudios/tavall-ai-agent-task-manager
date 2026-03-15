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
java -cp target/classes:target/dependency/* com.agenttaskmanager.app.cli.AgentTaskManagerCli validate /srv/AgentTaskManager
java -cp target/classes:target/dependency/* com.agenttaskmanager.app.cli.AgentTaskManagerCli run-workers tb_example /srv/AgentTaskManager worker
java -cp target/classes:target/dependency/* com.agenttaskmanager.app.cli.AgentTaskManagerCli serve-mcp-stdio
```
