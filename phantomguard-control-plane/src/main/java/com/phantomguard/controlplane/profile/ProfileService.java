package com.phantomguard.controlplane.profile;

import com.phantomguard.controlplane.config.ControlPlaneProperties;
import com.phantomguard.controlplane.domain.ChildProfileDocument;
import com.phantomguard.controlplane.repository.ChildProfileRepository;
import com.phantomguard.controlplane.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ChildProfileRepository repository;
    private final ControlPlaneProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProfileService(ChildProfileRepository repository, ControlPlaneProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Provisions a new child profile with a cryptographically secure clientId. */
    public ProfileResponse provision(String parentId, String childName) {
        ChildProfileDocument document = new ChildProfileDocument();
        document.setClientId(secureUuid());
        document.setParentId(parentId);
        document.setChildName(childName.trim());
        ChildProfileDocument saved = repository.save(document);
        log.info("Provisioned profile clientId={} for parentId={}", saved.getClientId(), parentId);
        return toResponse(saved);
    }

    public ProfileResponse getByClientId(String clientId) {
        return toResponse(requireByClientId(clientId));
    }

    public ChildProfileDocument requireByClientId(String clientId) {
        return repository.findByClientId(clientId)
                .orElseThrow(() -> new NotFoundException("No profile for clientId " + clientId));
    }

    public List<ProfileResponse> listByParent(String parentId) {
        return repository.findByParentIdOrderByCreatedAtAsc(parentId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ProfileResponse toResponse(ChildProfileDocument doc) {
        return new ProfileResponse(
                doc.getClientId(),
                doc.getParentId(),
                doc.getChildName(),
                properties.getPublicDohBaseUrl() + "/dns-query/" + doc.getClientId(),
                doc.getClientId() + "." + properties.getDotDomainSuffix(),
                "/api/profiles/" + doc.getClientId() + "/mobileconfig",
                doc.getBlockedCategories(),
                doc.getBlockedDomains(),
                doc.getAllowedDomains(),
                doc.getCreatedAt());
    }

    /**
     * UUIDv4 sourced from {@link SecureRandom} rather than the default
     * {@code Math.random()}-class entropy, since the clientId is the only
     * bearer credential a device presents.
     */
    private String secureUuid() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x40); // version 4
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80); // IETF variant
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFF);
        }
        return new UUID(msb, lsb).toString();
    }
}
