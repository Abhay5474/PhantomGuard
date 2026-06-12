package com.phantomguard.common.telemetry;

/** Resolution outcome attached to every telemetry event. */
public enum QueryStatus {
    ALLOWED,
    BLOCKED,
    /** Upstream failure: request answered with SERVFAIL but never blocked by policy. */
    ERROR
}
