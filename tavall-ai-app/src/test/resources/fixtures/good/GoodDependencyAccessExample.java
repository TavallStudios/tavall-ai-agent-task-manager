package fixtures.good;

public interface GoodDependencyAccessExample {

  default String describeTask(String taskId) {
    return "task:" + taskId;
  }
}
