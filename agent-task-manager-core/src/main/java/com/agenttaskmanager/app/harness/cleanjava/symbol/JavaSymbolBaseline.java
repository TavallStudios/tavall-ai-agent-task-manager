package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaSymbolBaseline(
    String correlationId,
    String status,
    boolean javaRepository,
    boolean reflectionAugmented,
    String baseRevision,
    JavaSourceSymbolCatalog catalog,
    List<String> targetSourcePaths,
    JavaSymbolNeighborhood neighborhood,
    JavaSymbolRunContext runContext,
    List<String> warnings
) {
}
