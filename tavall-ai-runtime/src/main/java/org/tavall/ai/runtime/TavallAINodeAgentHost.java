package org.tavall.ai.runtime;

import java.io.PrintStream;
import java.util.List;

/**
 * Host adapter for the Tavall AI Node Agent process.
 *
 * <p>The Tavall AI runtime owns process identity and composes installed role/domain modules. The
 * host adapter owns the externally-authorized job/session transport. Tavall Cloud may provide that
 * transport without moving AI execution or module ownership into the ordinary Cloud Node Agent.</p>
 */
public interface TavallAINodeAgentHost {
    int run(TavallAIRuntimeContext context, List<String> arguments, PrintStream output) throws Exception;
}
