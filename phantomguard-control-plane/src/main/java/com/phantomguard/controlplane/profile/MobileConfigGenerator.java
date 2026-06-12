package com.phantomguard.controlplane.profile;

import com.phantomguard.controlplane.config.ControlPlaneProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Renders an Apple Configuration Profile ({@code .mobileconfig}) that
 * provisions system-wide encrypted DNS (DoH) on iOS 14+ / macOS 11+ without
 * any client application. The DNSSettings payload pins the device to its
 * unique per-client resolver URL, which is how the data plane attributes
 * traffic to a child profile.
 *
 * <p>For supervised/MDM fleets, sign the emitted XML with an Apple-trusted
 * certificate so the install UI shows "Verified".
 */
@Component
public class MobileConfigGenerator {

    private final ControlPlaneProperties properties;

    public MobileConfigGenerator(ControlPlaneProperties properties) {
        this.properties = properties;
    }

    public String generate(String clientId, String childName) {
        String serverUrl = properties.getPublicDohBaseUrl() + "/dns-query/" + clientId;
        String payloadUuid = UUID.randomUUID().toString().toUpperCase();
        String profileUuid = UUID.randomUUID().toString().toUpperCase();
        String displayChild = escapeXml(childName);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>PayloadContent</key>
                    <array>
                        <dict>
                            <key>PayloadType</key>
                            <string>com.apple.dnsSettings.managed</string>
                            <key>PayloadVersion</key>
                            <integer>1</integer>
                            <key>PayloadIdentifier</key>
                            <string>com.phantomguard.dns.%s</string>
                            <key>PayloadUUID</key>
                            <string>%s</string>
                            <key>PayloadDisplayName</key>
                            <string>PhantomGuard Encrypted DNS</string>
                            <key>DNSSettings</key>
                            <dict>
                                <key>DNSProtocol</key>
                                <string>HTTPS</string>
                                <key>ServerURL</key>
                                <string>%s</string>
                            </dict>
                            <key>ProhibitDisablement</key>
                            <false/>
                        </dict>
                    </array>
                    <key>PayloadDescription</key>
                    <string>Routes all DNS traffic for %s through the PhantomGuard family protection resolver.</string>
                    <key>PayloadDisplayName</key>
                    <string>PhantomGuard - %s</string>
                    <key>PayloadIdentifier</key>
                    <string>com.phantomguard.profile.%s</string>
                    <key>PayloadOrganization</key>
                    <string>PhantomGuard</string>
                    <key>PayloadRemovalDisallowed</key>
                    <false/>
                    <key>PayloadType</key>
                    <string>Configuration</string>
                    <key>PayloadUUID</key>
                    <string>%s</string>
                    <key>PayloadVersion</key>
                    <integer>1</integer>
                </dict>
                </plist>
                """.formatted(clientId, payloadUuid, serverUrl, displayChild,
                displayChild, clientId, profileUuid);
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
