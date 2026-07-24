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

## Good Semantic Retrieval Flow

```text
raw content -> chunk by content type -> embed each chunk -> store vector + metadata + raw chunk in Qdrant
query text -> query embedding -> nearest chunks -> return original payload text/code to the worker
```

## Bad Cleanup Flow

```text
worker says "done" -> patch applied with no diff review or validation
```

## Good Deterministic Clean Java Flow

```text
loadCleanJavaTaskContext -> draft patch -> runCleanJavaHarness
  spoon source-shape feedback -> archunit architecture feedback -> cycle feedback -> approval gate
```

## Good CLI Usage

```bash
java --enable-preview -cp 'distribution/agent-task-manager/application.jar:distribution/agent-task-manager/libs/*' org.tavall.ai.app.AgentTaskManagerLauncher validate /srv/AgentTaskManager
java --enable-preview -cp 'distribution/agent-task-manager/application.jar:distribution/agent-task-manager/libs/*' org.tavall.ai.app.AgentTaskManagerLauncher run-workers tb_example /srv/AgentTaskManager worker
java --enable-preview -cp 'distribution/agent-task-manager/application.jar:distribution/agent-task-manager/libs/*' org.tavall.ai.app.AgentTaskManagerLauncher serve-mcp-stdio
```
