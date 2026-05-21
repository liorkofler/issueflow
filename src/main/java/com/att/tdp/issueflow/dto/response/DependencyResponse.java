package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.TicketStatus;

public record DependencyResponse(
        Long id,
        String title,
        TicketStatus status
) {}
