package org.tavall.ai.app.cleanjava;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.tavall.ai.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CleanJavaHarnessValidatorIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private CleanJavaHarnessValidator validator;

  @Test
  void shouldRegisterBundledHarnessValidator() {
    assertNotNull(validator);
  }
}

