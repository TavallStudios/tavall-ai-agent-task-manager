package cache;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ValidationSummaryCache extends AbstractCache<Map<String, Object>> {

  public ValidationSummaryCache() {
    super(10, TimeUnit.MINUTES);
  }
}
