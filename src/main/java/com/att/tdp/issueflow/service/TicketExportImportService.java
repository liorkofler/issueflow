package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.dto.response.ImportResultResponse;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketExportImportService {

    private static final String[] CSV_HEADERS = {"id", "title", "description", "status", "priority", "type", "assigneeId"};

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public byte[] exportToCSV(Long projectId) {
        projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedAtIsNull(projectId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.RFC4180.builder().setHeader(CSV_HEADERS).build())) {

            for (Ticket t : tickets) {
                printer.printRecord(
                        t.getId(),
                        t.getTitle(),
                        t.getDescription(),
                        t.getStatus(),
                        t.getPriority(),
                        t.getType(),
                        t.getAssignee() != null ? t.getAssignee().getId() : ""
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }
        return out.toByteArray();
    }

    @Transactional
    public ImportResultResponse importFromCSV(MultipartFile file, Long projectId, String currentUsername) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        Long actorId = userRepository.findByUsername(currentUsername).map(User::getId).orElse(null);

        int created = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader,
                     CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build())) {

            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                try {
                    String title = record.isMapped("title") ? record.get("title") : null;
                    String statusStr = record.isMapped("status") ? record.get("status") : null;
                    String priorityStr = record.isMapped("priority") ? record.get("priority") : null;
                    String typeStr = record.isMapped("type") ? record.get("type") : null;

                    if (title == null || title.isBlank()) {
                        errors.add("Row " + rowNum + ": title is required");
                        failed++;
                        continue;
                    }
                    if (statusStr == null || statusStr.isBlank()) {
                        errors.add("Row " + rowNum + ": status is required");
                        failed++;
                        continue;
                    }
                    if (priorityStr == null || priorityStr.isBlank()) {
                        errors.add("Row " + rowNum + ": priority is required");
                        failed++;
                        continue;
                    }
                    if (typeStr == null || typeStr.isBlank()) {
                        errors.add("Row " + rowNum + ": type is required");
                        failed++;
                        continue;
                    }

                    TicketStatus status;
                    TicketPriority priority;
                    TicketType type;

                    try {
                        status = TicketStatus.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        errors.add("Row " + rowNum + ": invalid status '" + statusStr + "'");
                        failed++;
                        continue;
                    }
                    try {
                        priority = TicketPriority.valueOf(priorityStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        errors.add("Row " + rowNum + ": invalid priority '" + priorityStr + "'");
                        failed++;
                        continue;
                    }
                    try {
                        type = TicketType.valueOf(typeStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        errors.add("Row " + rowNum + ": invalid type '" + typeStr + "'");
                        failed++;
                        continue;
                    }

                    String description = record.isMapped("description") ? record.get("description") : null;

                    User assignee = null;
                    if (record.isMapped("assigneeId")) {
                        String assigneeIdStr = record.get("assigneeId");
                        if (assigneeIdStr != null && !assigneeIdStr.isBlank()) {
                            try {
                                Long assigneeId = Long.parseLong(assigneeIdStr);
                                assignee = userRepository.findById(assigneeId).orElse(null);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    Ticket ticket = Ticket.builder()
                            .title(title)
                            .description(description)
                            .status(status)
                            .priority(priority)
                            .type(type)
                            .project(project)
                            .assignee(assignee)
                            .isOverdue(false)
                            .build();

                    Ticket saved = ticketRepository.save(ticket);

                    auditService.logAction("TICKET", saved.getId(), "CREATE",
                            actorId, AuditActorType.USER,
                            "{\"source\":\"csv_import\",\"title\":\"" + saved.getTitle() + "\"}");

                    created++;
                } catch (Exception e) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                    failed++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV file", e);
        }

        return new ImportResultResponse(created, failed, errors);
    }
}
