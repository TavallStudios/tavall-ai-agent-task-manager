package org.tavall.ai.bootstrap;

/** ServiceLoader entry point for one Tavall AI behavior/domain module. */
@FunctionalInterface
public interface TavallAIModuleProvider {
    TavallAIModule module();
}
