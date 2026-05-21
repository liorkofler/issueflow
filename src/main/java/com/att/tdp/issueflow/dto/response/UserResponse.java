package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.UserRole;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserRole role
) {}
