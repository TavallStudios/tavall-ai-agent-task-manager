package cache;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class WorkerSessionCache extends AbstractCache<Map<String, Object>> {

  public WorkerSessionCache() {
    super(2, TimeUnit.MINUTES);
  }
}
