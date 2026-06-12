package com.phantomguard.common.dns;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Zero-dependency DNS wire-format (RFC 1035) codec used on the resolution
 * critical path. Parsing is allocation-light and bounded; encoding reuses the
 * verbatim question bytes of the original query so synthesized responses are
 * byte-exact echoes of what the stub resolver sent.
 */
public final class DnsMessageUtil {

    private static final int HEADER_LENGTH = 12;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_POINTER_HOPS = 16;

    private static final int FLAG_QR_RESPONSE = 0x8000;
    private static final int FLAG_RD = 0x0100;
    private static final int FLAG_RA = 0x0080;

    public static final int RCODE_NOERROR = 0;
    public static final int RCODE_FORMERR = 1;
    public static final int RCODE_SERVFAIL = 2;
    public static final int RCODE_NXDOMAIN = 3;

    private DnsMessageUtil() {
    }

    /**
     * Parses the header and the first question of a DNS query message.
     *
     * @throws DnsParseException if the payload is malformed or carries no question
     */
    public static DnsQuestion parseQuestion(byte[] message) {
        if (message == null || message.length < HEADER_LENGTH) {
            throw new DnsParseException("DNS message shorter than 12-byte header");
        }
        ByteBuffer buf = ByteBuffer.wrap(message);
        int transactionId = buf.getShort() & 0xFFFF;
        int flags = buf.getShort() & 0xFFFF;
        int qdCount = buf.getShort() & 0xFFFF;
        buf.getShort(); // ANCOUNT
        buf.getShort(); // NSCOUNT
        buf.getShort(); // ARCOUNT

        if (qdCount < 1) {
            throw new DnsParseException("DNS query carries no question section");
        }

        int questionStart = buf.position();
        String name = readName(message, buf);
        if (buf.remaining() < 4) {
            throw new DnsParseException("Truncated QTYPE/QCLASS");
        }
        int qType = buf.getShort() & 0xFFFF;
        buf.getShort(); // QCLASS, assumed IN

        int questionEnd = buf.position();
        byte[] questionBytes = new byte[questionEnd - questionStart];
        System.arraycopy(message, questionStart, questionBytes, 0, questionBytes.length);

        return new DnsQuestion(transactionId, flags, name, DnsQueryType.fromCode(qType), qType, questionBytes);
    }

    /**
     * Synthesizes an NXDOMAIN response echoing the original question, used to
     * deny resolution of blocked domains.
     */
    public static byte[] buildNxDomainResponse(DnsQuestion question) {
        return buildEmptyResponse(question, RCODE_NXDOMAIN);
    }

    /** Synthesizes a SERVFAIL response, used when the upstream path is unavailable. */
    public static byte[] buildServFailResponse(DnsQuestion question) {
        return buildEmptyResponse(question, RCODE_SERVFAIL);
    }

    /**
     * Synthesizes a positive answer pointing the queried name at a safe landing
     * IP (block page). Supports A and AAAA queries; callers should fall back to
     * {@link #buildNxDomainResponse(DnsQuestion)} for other record types.
     */
    public static byte[] buildRedirectResponse(DnsQuestion question, InetAddress landingAddress, int ttlSeconds) {
        boolean isA = question.type() == DnsQueryType.A && landingAddress instanceof Inet4Address;
        boolean isAaaa = question.type() == DnsQueryType.AAAA && landingAddress instanceof Inet6Address;
        if (!isA && !isAaaa) {
            return buildNxDomainResponse(question);
        }

        byte[] rdata = landingAddress.getAddress();
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH + question.questionBytes().length + 12 + rdata.length);
        writeHeader(buf, question, RCODE_NOERROR, 1);
        buf.put(question.questionBytes());
        // Answer RR: compression pointer to QNAME at offset 12.
        buf.putShort((short) 0xC00C);
        buf.putShort((short) question.rawTypeCode());
        buf.putShort((short) 1); // CLASS IN
        buf.putInt(ttlSeconds);
        buf.putShort((short) rdata.length);
        buf.put(rdata);
        return buf.array();
    }

    private static byte[] buildEmptyResponse(DnsQuestion question, int rcode) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH + question.questionBytes().length);
        writeHeader(buf, question, rcode, 0);
        buf.put(question.questionBytes());
        return buf.array();
    }

    private static void writeHeader(ByteBuffer buf, DnsQuestion question, int rcode, int answerCount) {
        int flags = FLAG_QR_RESPONSE | FLAG_RA | rcode;
        if (question.isRecursionDesired()) {
            flags |= FLAG_RD;
        }
        buf.putShort((short) question.transactionId());
        buf.putShort((short) flags);
        buf.putShort((short) 1);            // QDCOUNT
        buf.putShort((short) answerCount);  // ANCOUNT
        buf.putShort((short) 0);            // NSCOUNT
        buf.putShort((short) 0);            // ARCOUNT
    }

    /**
     * Reads a (possibly compressed) domain name starting at the buffer's
     * current position. The buffer position is advanced past the in-place name
     * representation; pointer targets are followed without moving the position.
     */
    private static String readName(byte[] message, ByteBuffer buf) {
        StringBuilder name = new StringBuilder(64);
        int position = buf.position();
        int consumedEnd = -1;
        int hops = 0;

        while (true) {
            if (position >= message.length) {
                throw new DnsParseException("Name extends past end of message");
            }
            int length = message[position] & 0xFF;
            if (length == 0) {
                position++;
                break;
            }
            if ((length & 0xC0) == 0xC0) {
                if (position + 1 >= message.length) {
                    throw new DnsParseException("Truncated compression pointer");
                }
                if (++hops > MAX_POINTER_HOPS) {
                    throw new DnsParseException("Compression pointer loop detected");
                }
                if (consumedEnd < 0) {
                    consumedEnd = position + 2;
                }
                position = ((length & 0x3F) << 8) | (message[position + 1] & 0xFF);
                continue;
            }
            if (position + 1 + length > message.length) {
                throw new DnsParseException("Label extends past end of message");
            }
            if (name.length() + length + 1 > MAX_NAME_LENGTH) {
                throw new DnsParseException("Name exceeds 255 octets");
            }
            if (name.length() > 0) {
                name.append('.');
            }
            for (int i = 0; i < length; i++) {
                name.append((char) (message[position + 1 + i] & 0xFF));
            }
            position += length + 1;
        }

        buf.position(consumedEnd >= 0 ? consumedEnd : position);
        return name.toString().toLowerCase(Locale.ROOT);
    }
}
