package fixtures.bad;

public interface BadDependencyAccessExample {

  default Object getTaskService() {
    return null;
  }
}
