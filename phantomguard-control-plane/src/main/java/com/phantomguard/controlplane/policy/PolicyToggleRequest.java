package com.phantomguard.controlplane.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Administrative policy mutation.
 *
 * <p>{@code targetType=CATEGORY} accepts {@code BLOCK}/{@code UNBLOCK} with a
 * {@link com.phantomguard.common.policy.ContentCategory} name as the value.
 * {@code targetType=DOMAIN} additionally accepts {@code ALLOW}/{@code UNALLOW}
 * to manage explicit allow-overrides that win over category blocks
 * (e.g. unblock {@code wikipedia.org} while STREAMING stays blocked).
 */
public record PolicyToggleRequest(

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "clientId must be a UUID")
        String clientId,

        @NotNull
        TargetType targetType,

        @NotBlank
        @Size(max = 253)
        String value,

        @NotNull
        Action action) {

    public enum TargetType { DOMAIN, CATEGORY }

    public enum Action { BLOCK, UNBLOCK, ALLOW, UNALLOW }
}
