package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;
import java.util.Map;

public record JavaSourceSymbolCatalog(
    Map<String, JavaClassProfile> profilesByClassName,
    Map<String, List<String>> classesBySourcePath
) {
}
