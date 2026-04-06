/*
 * TJVD License (TJ Valentine's Discretionary License) - Version 1.0 (2025)
 *
 * Copyright (c) 2025 Taheesh Valentine
 *
 * This source code is protected under the TJVD License.
 * SEE LICENSE.TXT
 */

package org.tavall.ai.app.console.style;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogColors {
  public static final String RESET = "\u001B[0m";

  // Foreground colors
  public static final String BLACK = "\u001B[30m";
  public static final String RED = "\u001B[31m";
  public static final String GREEN = "\u001B[32m";
  public static final String YELLOW = "\u001B[33m";
  public static final String BLUE = "\u001B[34m";
  public static final String PURPLE = "\u001B[35m";
  public static final String CYAN = "\u001B[36m";
  public static final String WHITE = "\u001B[37m";
  public static final String GRAY = "\u001B[90m";

  // Bold variants (if supported by terminal)
  public static final String BOLD = "\u001B[1m";
  public static final String UNDERLINE = "\u001B[4m";
  public static final String REVERSED = "\u001B[7m";

  // Background colors
  public static final String BG_BLACK = "\u001B[40m";
  public static final String BG_RED = "\u001B[41m";
  public static final String BG_GREEN = "\u001B[42m";
  public static final String BG_YELLOW = "\u001B[43m";
  public static final String BG_BLUE = "\u001B[44m";
  public static final String BG_PURPLE = "\u001B[45m";
  public static final String BG_CYAN = "\u001B[46m";
  public static final String BG_WHITE = "\u001B[47m";
  public static final String BG_GRAY = "\u001B[100m";

  // Foreground helpers
  public static String black(String msg) {
    return BLACK + msg + RESET;
  }

  public static String red(String msg) {
    return RED + msg + RESET;
  }

  public static String green(String msg) {
    return GREEN + msg + RESET;
  }

  public static String yellow(String msg) {
    return YELLOW + msg + RESET;
  }

  public static String blue(String msg) {
    return BLUE + msg + RESET;
  }

  public static String purple(String msg) {
    return PURPLE + msg + RESET;
  }

  public static String cyan(String msg) {
    return CYAN + msg + RESET;
  }

  public static String white(String msg) {
    return WHITE + msg + RESET;
  }

  public static String gray(String msg) {
    return GRAY + msg + RESET;
  }

  // Style helpers
  public static String bold(String msg) {
    return BOLD + msg + RESET;
  }

  public static String underline(String msg) {
    return UNDERLINE + msg + RESET;
  }

  public static String reversed(String msg) {
    return REVERSED + msg + RESET;
  }

  // Background helpers
  public static String bgBlack(String msg) {
    return BG_BLACK + msg + RESET;
  }

  public static String bgRed(String msg) {
    return BG_RED + msg + RESET;
  }

  public static String bgGreen(String msg) {
    return BG_GREEN + msg + RESET;
  }

  public static String bgYellow(String msg) {
    return BG_YELLOW + msg + RESET;
  }

  public static String bgBlue(String msg) {
    return BG_BLUE + msg + RESET;
  }

  public static String bgPurple(String msg) {
    return BG_PURPLE + msg + RESET;
  }

  public static String bgCyan(String msg) {
    return BG_CYAN + msg + RESET;
  }

  public static String bgWhite(String msg) {
    return BG_WHITE + msg + RESET;
  }

  public static String bgGray(String msg) {
    return BG_GRAY + msg + RESET;
  }

  // Combo helpers (optional but handy)
  public static String boldRed(String msg) {
    return BOLD + RED + msg + RESET;
  }

  public static String boldGreen(String msg) {
    return BOLD + GREEN + msg + RESET;
  }

  public static String boldYellow(String msg) {
    return BOLD + YELLOW + msg + RESET;
  }

  public static String underlineRed(String msg) {
    return UNDERLINE + RED + msg + RESET;
  }

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([a-zA-Z_]+)%", Pattern.CASE_INSENSITIVE);
  private static final Map<String, String> PLACEHOLDERS;

  static {
    Map<String, String> map = new HashMap<>();
    map.put("RESET", RESET);
    map.put("BLACK", BLACK);
    map.put("RED", RED);
    map.put("GREEN", GREEN);
    map.put("YELLOW", YELLOW);
    map.put("BLUE", BLUE);
    map.put("PURPLE", PURPLE);
    map.put("CYAN", CYAN);
    map.put("WHITE", WHITE);
    map.put("GRAY", GRAY);
    map.put("BOLD", BOLD);
    map.put("UNDERLINE", UNDERLINE);
    map.put("REVERSED", REVERSED);
    map.put("BG_BLACK", BG_BLACK);
    map.put("BG_RED", BG_RED);
    map.put("BG_GREEN", BG_GREEN);
    map.put("BG_YELLOW", BG_YELLOW);
    map.put("BG_BLUE", BG_BLUE);
    map.put("BG_PURPLE", BG_PURPLE);
    map.put("BG_CYAN", BG_CYAN);
    map.put("BG_WHITE", BG_WHITE);
    map.put("BG_GRAY", BG_GRAY);
    PLACEHOLDERS = Collections.unmodifiableMap(map);
  }

  /**
   * Replaces placeholder tokens (e.g. {@code %yellow%}) with their ANSI counterparts.
   * Unknown tokens are left untouched to avoid corrupting the message.
   */
  public static String applyPlaceholders(String message) {
    if (message == null || message.isEmpty()) {
      return message;
    }

    Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
    StringBuilder sb = new StringBuilder(message.length());
    int last = 0;
    while (matcher.find()) {
      sb.append(message, last, matcher.start());
      String placeholder = matcher.group(1);
      String code = PLACEHOLDERS.get(placeholder.toUpperCase(Locale.ROOT));
      if (code != null) {
        sb.append(code);
      } else {
        sb.append(matcher.group());
      }
      last = matcher.end();
    }
    sb.append(message, last, message.length());
    return sb.toString();
  }

  public static Map<String, String> getPlaceholders() {
    return PLACEHOLDERS;
  }
}

