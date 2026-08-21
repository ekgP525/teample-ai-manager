package com.teample.dto.auth;

public record AuthUserResponse(
        String authUserId,
        String memberKey,
        String email,
        boolean admin,
        String authMode
) {
}