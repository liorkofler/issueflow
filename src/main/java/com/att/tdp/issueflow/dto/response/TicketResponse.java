package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.TicketPriority;
import com.att.tdp.issueflow.entity.TicketStatus;
import com.att.tdp.issueflow.entity.TicketType;

import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketType type,
        Long projectId,
        Long assigneeId,
        LocalDateTime dueDate,
        Boolean isOverdue
) {
}
