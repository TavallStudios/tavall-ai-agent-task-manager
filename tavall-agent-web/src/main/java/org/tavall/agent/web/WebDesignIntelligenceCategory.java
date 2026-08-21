package org.tavall.agent.web;

/** Stable Web Agent categories stored in the generic product-intelligence system. */
public enum WebDesignIntelligenceCategory {
    PRODUCT_IDENTITY("web.product-identity"),
    AUDIENCE("web.audience"),
    BRAND("web.brand"),
    VISUAL_PRINCIPLE("web.visual-principle"),
    FORBIDDEN_PATTERN("web.forbidden-pattern"),
    TYPOGRAPHY("web.typography"),
    SPACING("web.spacing"),
    COLOR("web.color"),
    COMPONENT_LANGUAGE("web.component-language"),
    INTERACTION("web.interaction"),
    REFERENCE("web.reference"),
    DESIGN_DECISION("web.design-decision");

    private final String storageKey;

    WebDesignIntelligenceCategory(String storageKey) {
        this.storageKey = storageKey;
    }

    public String storageKey() {
        return storageKey;
    }
}
