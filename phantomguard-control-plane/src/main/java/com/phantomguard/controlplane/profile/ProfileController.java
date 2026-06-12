package com.phantomguard.controlplane.profile;

import com.phantomguard.controlplane.config.ControlPlaneProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * MODULE A — profile provisioning and zero-app device onboarding.
 */
@Validated
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final ProfileService profileService;
    private final MobileConfigGenerator mobileConfigGenerator;
    private final ControlPlaneProperties properties;

    public ProfileController(ProfileService profileService,
                             MobileConfigGenerator mobileConfigGenerator,
                             ControlPlaneProperties properties) {
        this.profileService = profileService;
        this.mobileConfigGenerator = mobileConfigGenerator;
        this.properties = properties;
    }

    /** Provisions a child profile and returns its unique resolver endpoints. */
    @GetMapping("/generate")
    public ProfileResponse generate(
            @RequestParam @NotBlank @Size(max = 64) String parentId,
            @RequestParam @NotBlank @Size(max = 64) String childName) {
        return profileService.provision(parentId, childName);
    }

    @GetMapping
    public List<ProfileResponse> listByParent(@RequestParam @NotBlank String parentId) {
        return profileService.listByParent(parentId);
    }

    @GetMapping("/{clientId}")
    public ProfileResponse get(@PathVariable @Pattern(regexp = UUID_REGEX) String clientId) {
        return profileService.getByClientId(clientId);
    }

    /**
     * iOS/macOS onboarding artifact: Safari opens this URL on the child device
     * and the OS offers to install the encrypted-DNS configuration profile.
     */
    @GetMapping(value = "/{clientId}/mobileconfig")
    public ResponseEntity<byte[]> mobileConfig(@PathVariable @Pattern(regexp = UUID_REGEX) String clientId) {
        ProfileResponse profile = profileService.getByClientId(clientId);
        byte[] payload = mobileConfigGenerator
                .generate(profile.clientId(), profile.childName())
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-apple-aspen-config"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"phantomguard-" + profile.childName()
                                .replaceAll("[^A-Za-z0-9-]", "_") + ".mobileconfig\"")
                .body(payload);
    }

    /**
     * Android onboarding payload: the strict Private DNS hostname plus the
     * manual steps the dashboard renders for the parent.
     */
    @GetMapping("/{clientId}/android")
    public Map<String, Object> androidOnboarding(@PathVariable @Pattern(regexp = UUID_REGEX) String clientId) {
        ProfileResponse profile = profileService.getByClientId(clientId);
        String hostname = profile.clientId() + "." + properties.getDotDomainSuffix();
        return Map.of(
                "clientId", profile.clientId(),
                "childName", profile.childName(),
                "privateDnsHostname", hostname,
                "steps", List.of(
                        "Open Settings on the Android device",
                        "Go to Network & Internet > Private DNS",
                        "Select 'Private DNS provider hostname'",
                        "Enter: " + hostname,
                        "Tap Save — all DNS traffic is now protected"));
    }
}
