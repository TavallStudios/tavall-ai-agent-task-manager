package org.tavall.agent.web;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** One materially distinct design direction in a Web Agent comparison. */
public record WebDesignCandidate(
        String id,
        String label,
        String rationale,
        Set<String> evidenceReferences
) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]+");

    public WebDesignCandidate {
        id = requireIdentifier(id, "id");
        label = requireText(label, "label");
        rationale = requireText(rationale, "rationale");

        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        if (evidenceReferences != null) {
            for (String reference : evidenceReferences) {
                evidence.add(requireText(reference, "evidenceReferences entry"));
            }
        }
        evidenceReferences = Collections.unmodifiableSet(evidence);
    }

    static String requireIdentifier(String value, String fieldName) {
        String identifier = requireText(value, fieldName);
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(fieldName + " must match " + IDENTIFIER.pattern());
        }
        return identifier;
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
