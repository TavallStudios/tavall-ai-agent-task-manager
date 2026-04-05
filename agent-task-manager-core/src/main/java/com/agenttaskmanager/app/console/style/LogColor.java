/*
 * TJVD License (TJ Valentine's Discretionary License) - Version 1.0 (2025)
 *
 * Copyright (c) 2025 Taheesh Valentine
 *
 * This source code is protected under the TJVD License.
 * SEE LICENSE.TXT
 */

package com.agenttaskmanager.app.console.style;

public enum LogColor {
  RESET(LogColors.RESET),
  BLACK(LogColors.BLACK),
  RED(LogColors.RED),
  GREEN(LogColors.GREEN),
  YELLOW(LogColors.YELLOW),
  BLUE(LogColors.BLUE),
  PURPLE(LogColors.PURPLE),
  CYAN(LogColors.CYAN),
  WHITE(LogColors.WHITE),
  GRAY(LogColors.GRAY),
  BOLD(LogColors.BOLD),
  UNDERLINE(LogColors.UNDERLINE),
  REVERSED(LogColors.REVERSED),
  BG_BLACK(LogColors.BG_BLACK),
  BG_RED(LogColors.BG_RED),
  BG_GREEN(LogColors.BG_GREEN),
  BG_YELLOW(LogColors.BG_YELLOW),
  BG_BLUE(LogColors.BG_BLUE),
  BG_PURPLE(LogColors.BG_PURPLE),
  BG_CYAN(LogColors.BG_CYAN),
  BG_WHITE(LogColors.BG_WHITE),
  BG_GRAY(LogColors.BG_GRAY);

  private final String code;

  LogColor(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public boolean isReset() {
    return this == RESET;
  }

  @Override
  public String toString() {
    return code;
  }
}
