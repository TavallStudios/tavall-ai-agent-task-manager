package org.tavall.agent.review;

public record ReviewPublicationPolicy(boolean publish) {
    public static ReviewPublicationPolicy localOnly() {
        return new ReviewPublicationPolicy(false);
    }

    public static ReviewPublicationPolicy publishToProvider() {
        return new ReviewPublicationPolicy(true);
    }
}
