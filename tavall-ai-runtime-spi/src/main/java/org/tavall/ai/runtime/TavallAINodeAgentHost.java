package org.tavall.ai.runtime;

import org.tavall.ai.agent.role.TavallAIAgentRoleRegistry;

import java.io.PrintStream;
import java.util.List;

/**
 * Host integration boundary for the Tavall AI Node Agent runtime.
 *
 * <p>Tavall AI owns the process and installed role modules. The host adapter owns the externally
 * authorized job/session transport. Tavall Cloud may provide that transport without moving AI
 * execution or role ownership into the ordinary Cloud Node Agent.</p>
 */
public interface TavallAINodeAgentHost {
    int run(TavallAIAgentRoleRegistry roles, List<String> arguments, PrintStream output) throws Exception;
}
