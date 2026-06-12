package com.phantomguard.common.policy;

/** Content categories a parent can toggle as a unit. */
public enum ContentCategory {
    ADULT_CONTENT("Adult Content"),
    SOCIAL_MEDIA("Social Media"),
    GAMING("Gaming"),
    GAMBLING("Gambling"),
    STREAMING("Video Streaming");

    private final String displayName;

    ContentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
