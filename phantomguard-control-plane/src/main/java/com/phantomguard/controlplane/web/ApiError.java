package com.phantomguard.controlplane.web;

import java.time.Instant;

/** Uniform error envelope returned by every REST endpoint. */
public record ApiError(int status, String error, String message, Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now());
    }
}
