package com.phantomguard.common.dns;

/**
 * Raised when an inbound payload cannot be interpreted as a valid DNS
 * wire-format message (RFC 1035). The data plane converts this into a
 * FORMERR response or an HTTP 400 depending on the transport.
 */
public class DnsParseException extends RuntimeException {

    public DnsParseException(String message) {
        super(message);
    }

    public DnsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
