package com.teample.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminTestAuthService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    @Value("${admin.test-enabled:false}")
    private boolean enabled;

    @Value("${admin.test-id:${ADMIN_TEST_ID:}}")
    private String adminId = "";

    @Value("${admin.test-password:${ADMIN_TEST_PASSWORD:}}")
    private String adminPassword = "";

    public boolean matches(String id, String password) {
        return enabled && environment != null
                && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev"))
                && !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))
                && !adminId.isBlank() && adminPassword.length() >= 16
                && secureEquals(adminId, id) && secureEquals(adminPassword, password);
    }

    public String adminId() {
        return adminId;
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}