package cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SemanticContextCache extends AbstractCache<List<Map<String, Object>>> {

  public SemanticContextCache() {
    super(5, TimeUnit.MINUTES);
  }
}
