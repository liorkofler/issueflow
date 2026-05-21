package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.request.CreateTicketRequest;
import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.dto.response.TicketResponse;
import com.att.tdp.issueflow.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getAllByProject(@RequestParam Long projectId) {
        return ResponseEntity.ok(ticketService.getAllByProject(projectId));
    }

    // Must be declared before /{ticketId} to avoid Spring treating "deleted" as a path variable
    @GetMapping("/deleted")
    public ResponseEntity<List<TicketResponse>> getDeletedTickets(
            @RequestParam Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ticketService.getDeletedTickets(projectId, userDetails.getUsername()));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ticketService.createTicket(request, userDetails.getUsername()));
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long ticketId,
            @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ticketService.updateTicket(ticketId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> softDeleteTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        ticketService.softDeleteTicket(ticketId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/restore")
    public ResponseEntity<Void> restoreTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        ticketService.restoreTicket(ticketId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
