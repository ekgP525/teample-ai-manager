package com.teample.security;

public record AuthenticatedUser(String authUserId, String memberKey, String email) {
}