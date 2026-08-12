package org.tavall.ai.runtime;

import org.tavall.ai.agent.role.TavallAIAgentRoleRegistry;

import java.io.PrintStream;
import java.util.List;

/**
 * Host adapter for the Tavall AI Node Agent process.
 *
 * <p>The Tavall AI runtime owns the process and installed roles. The host adapter owns the
 * externally-authorized job/session transport. Tavall Cloud may provide that transport without
 * moving AI execution or role ownership into the ordinary Cloud Node Agent.</p>
 */
public interface TavallAINodeAgentHost {
    int run(TavallAIAgentRoleRegistry roles, List<String> arguments, PrintStream output) throws Exception;
}
