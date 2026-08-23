package com.teample.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminTestAuthService {

    @Value("${admin.test-id:${ADMIN_TEST_ID:admin}}")
    private String adminId = "admin";

    @Value("${admin.test-password:${ADMIN_TEST_PASSWORD:1234}}")
    private String adminPassword = "1234";

    public boolean matches(String id, String password) {
        return secureEquals(adminId, id) && secureEquals(adminPassword, password);
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