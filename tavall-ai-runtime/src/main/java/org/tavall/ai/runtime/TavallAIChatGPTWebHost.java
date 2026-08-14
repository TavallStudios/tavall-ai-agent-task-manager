package org.tavall.ai.runtime;

import java.io.PrintStream;
import java.util.List;

/**
 * Authorized host adapter for the Tavall AI ChatGPT Web runtime.
 *
 * <p>The host owns web conversation/session mechanics and authority correlation. It is separate
 * from Tavall Cloud's inbound ChatGPT-to-CONTROL adapter and must not widen the job/lease scope
 * supplied by Cloud or another authoritative caller.</p>
 */
public interface TavallAIChatGPTWebHost {
    int run(TavallAIRuntimeContext context, List<String> arguments, PrintStream output) throws Exception;
}
