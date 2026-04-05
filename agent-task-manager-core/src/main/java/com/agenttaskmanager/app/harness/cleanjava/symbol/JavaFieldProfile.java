package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaFieldProfile(
    String name,
    String type,
    List<String> modifiers,
    List<String> annotations,
    String comment
) {
}
