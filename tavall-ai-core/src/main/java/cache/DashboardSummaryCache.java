package cache;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class DashboardSummaryCache extends AbstractCache<Map<String, Object>> {

  public DashboardSummaryCache() {
    super(30, TimeUnit.SECONDS);
  }
}
