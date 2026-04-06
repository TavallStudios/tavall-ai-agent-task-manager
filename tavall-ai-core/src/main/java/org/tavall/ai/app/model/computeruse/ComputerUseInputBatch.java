package org.tavall.ai.app.model.computeruse;

import java.util.List;

public record ComputerUseInputBatch(
    boolean activateWindow,
    List<KeyboardAction> keyboardActions,
    List<MouseAction> mouseActions
) {

  public record KeyboardAction(
      String key,
      Integer virtualKey,
      Integer scanCode,
      String action,
      int delayMs,
      boolean extended
  ) {
  }

  public record MouseAction(
      String action,
      int x,
      int y,
      String button,
      int wheelDelta,
      int delayMs,
      String coordinates
  ) {
  }
}

