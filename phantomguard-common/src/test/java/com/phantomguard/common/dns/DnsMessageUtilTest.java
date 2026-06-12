package com.phantomguard.common.dns;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DnsMessageUtilTest {

    private static byte[] buildQuery(int id, String name, int qtype, boolean recursionDesired) {
        String[] labels = name.split("\\.");
        int nameLength = 1; // root terminator
        for (String label : labels) {
            nameLength += 1 + label.length();
        }
        ByteBuffer buf = ByteBuffer.allocate(12 + nameLength + 4);
        buf.putShort((short) id);
        buf.putShort((short) (recursionDesired ? 0x0100 : 0x0000));
        buf.putShort((short) 1);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        for (String label : labels) {
            buf.put((byte) label.length());
            buf.put(label.getBytes(StandardCharsets.US_ASCII));
        }
        buf.put((byte) 0);
        buf.putShort((short) qtype);
        buf.putShort((short) 1);
        return buf.array();
    }

    @Test
    void parsesStandardAQuery() {
        byte[] query = buildQuery(0xBEEF, "www.TikTok.com", 1, true);
        DnsQuestion question = DnsMessageUtil.parseQuestion(query);

        assertThat(question.transactionId()).isEqualTo(0xBEEF);
        assertThat(question.name()).isEqualTo("www.tiktok.com");
        assertThat(question.type()).isEqualTo(DnsQueryType.A);
        assertThat(question.isRecursionDesired()).isTrue();
    }

    @Test
    void nxDomainResponseEchoesIdAndQuestion() {
        byte[] query = buildQuery(0x1234, "blocked.example.com", 28, true);
        DnsQuestion question = DnsMessageUtil.parseQuestion(query);
        byte[] response = DnsMessageUtil.buildNxDomainResponse(question);

        ByteBuffer buf = ByteBuffer.wrap(response);
        assertThat(buf.getShort() & 0xFFFF).isEqualTo(0x1234);
        int flags = buf.getShort() & 0xFFFF;
        assertThat(flags & 0x8000).isNotZero();          // QR = response
        assertThat(flags & 0x000F).isEqualTo(3);         // RCODE = NXDOMAIN
        assertThat(buf.getShort() & 0xFFFF).isEqualTo(1); // QDCOUNT
        assertThat(buf.getShort() & 0xFFFF).isZero();     // ANCOUNT
    }

    @Test
    void redirectResponseCarriesLandingIpForAQuery() throws Exception {
        byte[] query = buildQuery(0x0042, "ads.example.com", 1, true);
        DnsQuestion question = DnsMessageUtil.parseQuestion(query);
        byte[] response = DnsMessageUtil.buildRedirectResponse(
                question, InetAddress.getByName("10.10.10.10"), 60);

        ByteBuffer buf = ByteBuffer.wrap(response);
        buf.position(6);
        assertThat(buf.getShort() & 0xFFFF).isEqualTo(1); // ANCOUNT
        byte[] rdata = new byte[4];
        buf.position(response.length - 4);
        buf.get(rdata);
        assertThat(InetAddress.getByAddress(rdata).getHostAddress()).isEqualTo("10.10.10.10");
    }

    @Test
    void redirectFallsBackToNxDomainForNonAddressTypes() throws Exception {
        byte[] query = buildQuery(0x0007, "blocked.example.com", 16, true); // TXT
        DnsQuestion question = DnsMessageUtil.parseQuestion(query);
        byte[] response = DnsMessageUtil.buildRedirectResponse(
                question, InetAddress.getByName("10.10.10.10"), 60);

        ByteBuffer buf = ByteBuffer.wrap(response);
        buf.position(2);
        assertThat((buf.getShort() & 0xFFFF) & 0x000F).isEqualTo(3);
    }

    @Test
    void rejectsTruncatedMessages() {
        assertThatThrownBy(() -> DnsMessageUtil.parseQuestion(new byte[]{1, 2, 3}))
                .isInstanceOf(DnsParseException.class);
    }

    @Test
    void rejectsMessagesWithoutQuestion() {
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.putShort((short) 1).putShort((short) 0).putShort((short) 0)
                .putShort((short) 0).putShort((short) 0).putShort((short) 0);
        assertThatThrownBy(() -> DnsMessageUtil.parseQuestion(buf.array()))
                .isInstanceOf(DnsParseException.class);
    }
}
