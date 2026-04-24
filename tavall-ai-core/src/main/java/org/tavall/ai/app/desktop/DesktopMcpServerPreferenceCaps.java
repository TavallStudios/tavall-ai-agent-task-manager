package org.tavall.ai.app.desktop;

import java.util.Map;

public record DesktopMcpServerPreferenceCaps(
    boolean enabled,
    DesktopMcpServerMode mode,
    Map<String, String> envOverrides
) {
}
