package com.phantomguard.dataplane.doh;

import com.phantomguard.common.dns.DnsParseException;
import com.phantomguard.dataplane.engine.DnsResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * RFC 8484 DNS-over-HTTPS endpoint, the transport used by iOS/macOS via the
 * provisioned {@code .mobileconfig} profile. Supports both verbs the RFC
 * defines:
 *
 * <ul>
 *   <li>{@code GET /dns-query/{clientId}?dns=<base64url(wire-format)>}</li>
 *   <li>{@code POST /dns-query/{clientId}} with {@code application/dns-message} body</li>
 * </ul>
 */
@RestController
public class DohController {

    private static final Logger log = LoggerFactory.getLogger(DohController.class);

    public static final MediaType DNS_MESSAGE = MediaType.parseMediaType("application/dns-message");

    private static final Pattern CLIENT_ID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Base64.Decoder BASE64_URL = Base64.getUrlDecoder();
    private static final int MAX_QUERY_BYTES = 4096;

    private final DnsResolutionService resolutionService;

    public DohController(DnsResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @GetMapping(value = "/dns-query/{clientId}", produces = "application/dns-message")
    public Mono<ResponseEntity<byte[]>> resolveGet(@PathVariable String clientId,
                                                   @RequestParam("dns") String dnsParam) {
        validateClientId(clientId);
        final byte[] query;
        try {
            query = BASE64_URL.decode(dnsParam);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dns parameter is not valid base64url");
        }
        return resolve(clientId, query);
    }

    @PostMapping(value = "/dns-query/{clientId}",
            consumes = "application/dns-message",
            produces = "application/dns-message")
    public Mono<ResponseEntity<byte[]>> resolvePost(@PathVariable String clientId,
                                                    @RequestBody byte[] body) {
        validateClientId(clientId);
        return resolve(clientId, body);
    }

    private Mono<ResponseEntity<byte[]>> resolve(String clientId, byte[] query) {
        if (query.length == 0 || query.length > MAX_QUERY_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DNS payload size out of bounds");
        }
        return resolutionService.resolve(clientId, query)
                .map(response -> ResponseEntity.ok()
                        .contentType(DNS_MESSAGE)
                        .header("Cache-Control", "no-store")
                        .body(response));
    }

    private static void validateClientId(String clientId) {
        if (!CLIENT_ID_PATTERN.matcher(clientId).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown resolver endpoint");
        }
    }

    @ExceptionHandler(DnsParseException.class)
    public ResponseEntity<String> handleParseError(DnsParseException e) {
        log.debug("Rejected malformed DNS payload: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Malformed DNS message");
    }
}
