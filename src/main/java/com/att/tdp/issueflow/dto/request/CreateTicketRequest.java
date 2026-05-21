package com.att.tdp.issueflow.dto.request;

import com.att.tdp.issueflow.entity.TicketPriority;
import com.att.tdp.issueflow.entity.TicketStatus;
import com.att.tdp.issueflow.entity.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateTicketRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private TicketStatus status;

    @NotNull
    private TicketPriority priority;

    @NotNull
    private TicketType type;

    @NotNull
    private Long projectId;

    private Long assigneeId;

    private LocalDateTime dueDate;
}
