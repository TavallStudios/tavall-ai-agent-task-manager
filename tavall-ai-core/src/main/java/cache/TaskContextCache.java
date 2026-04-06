package cache;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class TaskContextCache extends AbstractCache<Map<String, Object>> {

  public TaskContextCache() {
    super(10, TimeUnit.MINUTES);
  }
}
