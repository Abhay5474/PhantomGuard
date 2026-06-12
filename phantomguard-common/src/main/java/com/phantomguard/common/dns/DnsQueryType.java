package com.phantomguard.common.dns;

/**
 * Subset of DNS RR types (RFC 1035 / RFC 3596) relevant to the resolution
 * critical path. Unknown codes are preserved through {@link #OTHER} so the
 * resolver remains a transparent proxy for record types it does not inspect.
 */
public enum DnsQueryType {
    A(1),
    NS(2),
    CNAME(5),
    SOA(6),
    PTR(12),
    MX(15),
    TXT(16),
    AAAA(28),
    SRV(33),
    SVCB(64),
    HTTPS(65),
    ANY(255),
    OTHER(-1);

    private final int code;

    DnsQueryType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static DnsQueryType fromCode(int code) {
        for (DnsQueryType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return OTHER;
    }
}
