package com.att.tdp.issueflow.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
