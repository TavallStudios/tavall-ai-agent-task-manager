package cache;

import org.tavall.ai.app.harness.cleanjava.symbol.JavaContractDeltaReport;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class JavaContractDeltaCache extends AbstractCache<JavaContractDeltaReport> {

  public JavaContractDeltaCache() {
    super(10, TimeUnit.MINUTES);
  }
}

