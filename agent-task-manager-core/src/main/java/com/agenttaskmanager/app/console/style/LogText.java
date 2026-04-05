/*
 * TJVD License (TJ Valentine's Discretionary License) - Version 1.0 (2025)
 *
 * Copyright (c) 2025 Taheesh Valentine
 *
 * This source code is protected under the TJVD License.
 * SEE LICENSE.TXT
 */

package com.agenttaskmanager.app.console.style;

public final class LogText {

  private final StringBuilder builder = new StringBuilder();

  private LogText() {
  }

  public static LogText create() {
    return new LogText();
  }

  public static LogText of(String text) {
    return create().append(text);
  }

  public LogText append(String text) {
    builder.append(text);
    return this;
  }

  public LogText append(LogColor color) {
    builder.append(color.code());
    return this;
  }

  public LogText append(LogColor color, String text) {
    if (text != null && !text.isEmpty()) {
      builder.append(color.code()).append(text);
      if (!color.isReset()) {
        builder.append(LogColor.RESET.code());
      }
    }
    return this;
  }

  public LogText reset() {
    builder.append(LogColor.RESET.code());
    return this;
  }

  public String build() {
    return builder.toString();
  }

  @Override
  public String toString() {
    return build();
  }
}
