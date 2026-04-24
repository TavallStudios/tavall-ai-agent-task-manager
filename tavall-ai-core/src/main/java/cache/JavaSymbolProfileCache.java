package cache;

import org.tavall.ai.app.harness.cleanjava.symbol.JavaClassProfile;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class JavaSymbolProfileCache extends AbstractCache<JavaClassProfile> {

  public JavaSymbolProfileCache() {
    super(10, TimeUnit.MINUTES);
  }
}

