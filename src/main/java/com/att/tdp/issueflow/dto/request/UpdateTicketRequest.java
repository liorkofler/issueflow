package com.att.tdp.issueflow.dto.request;

import com.att.tdp.issueflow.entity.TicketPriority;
import com.att.tdp.issueflow.entity.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateTicketRequest {

    private String title;

    private String description;

    private TicketStatus status;

    private TicketPriority priority;

    private Long assigneeId;

    private LocalDateTime dueDate;
}
