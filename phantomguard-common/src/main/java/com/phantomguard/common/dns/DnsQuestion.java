package com.phantomguard.common.dns;

/**
 * Immutable view of the first entry of a DNS Question Section.
 *
 * @param transactionId 16-bit message ID, echoed back in synthesized answers
 * @param flags         raw header flags of the query (RD bit is propagated)
 * @param name          fully qualified, lower-cased domain name without trailing dot
 * @param type          parsed query type (A, AAAA, ...)
 * @param rawTypeCode   original 16-bit QTYPE value
 * @param questionBytes verbatim bytes of the question section (QNAME+QTYPE+QCLASS),
 *                      reused when synthesizing block responses
 */
public record DnsQuestion(
        int transactionId,
        int flags,
        String name,
        DnsQueryType type,
        int rawTypeCode,
        byte[] questionBytes) {

    public boolean isRecursionDesired() {
        return (flags & 0x0100) != 0;
    }
}
