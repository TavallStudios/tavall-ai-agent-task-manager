package org.tavall.ai.context;

/** Adapter for one externally authorized source of project context. */
public interface TavallAIProjectContextSource {
    String sourceType();

    TavallAIProjectContextBundle resolve(TavallAIProjectContextRequest request) throws Exception;
}
