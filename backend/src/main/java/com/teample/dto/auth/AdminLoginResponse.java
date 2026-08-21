package com.teample.dto.auth;

public record AdminLoginResponse(boolean admin, String adminId, String authMode) {
}