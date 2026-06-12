package com.phantomguard.controlplane.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Source-of-truth configuration document for one protected child device
 * profile. The Redis policy sets are a derived, evictable projection of the
 * three rule sets stored here.
 */
@Document("child_profiles")
public class ChildProfileDocument {

    @Id
    private String id;

    /** Cryptographically secure UUID embedded in DoH path / DoT hostname. */
    @Indexed(unique = true)
    private String clientId;

    @Indexed
    private String parentId;

    private String childName;

    private Set<String> blockedCategories = new HashSet<>();
    private Set<String> blockedDomains = new HashSet<>();
    private Set<String> allowedDomains = new HashSet<>();

    private boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }
    public Set<String> getBlockedCategories() { return blockedCategories; }
    public void setBlockedCategories(Set<String> blockedCategories) { this.blockedCategories = blockedCategories; }
    public Set<String> getBlockedDomains() { return blockedDomains; }
    public void setBlockedDomains(Set<String> blockedDomains) { this.blockedDomains = blockedDomains; }
    public Set<String> getAllowedDomains() { return allowedDomains; }
    public void setAllowedDomains(Set<String> allowedDomains) { this.allowedDomains = allowedDomains; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
