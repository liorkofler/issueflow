package com.att.tdp.issueflow.dto.response;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId
) {}
