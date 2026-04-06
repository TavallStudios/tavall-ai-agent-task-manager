package cache;

import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolNeighborhood;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class JavaSymbolNeighborhoodCache extends AbstractCache<JavaSymbolNeighborhood> {

  public JavaSymbolNeighborhoodCache() {
    super(10, TimeUnit.MINUTES);
  }
}

