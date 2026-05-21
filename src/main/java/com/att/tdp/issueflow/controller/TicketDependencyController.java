package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.request.AddDependencyRequest;
import com.att.tdp.issueflow.dto.response.DependencyResponse;
import com.att.tdp.issueflow.service.TicketDependencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
@RequiredArgsConstructor
public class TicketDependencyController {

    private final TicketDependencyService ticketDependencyService;

    @PostMapping
    public ResponseEntity<Void> addDependency(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddDependencyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        ticketDependencyService.addDependency(ticketId, request.getBlockedBy(), userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<DependencyResponse>> getDependencies(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketDependencyService.getDependencies(ticketId));
    }

    @DeleteMapping("/{blockerId}")
    public ResponseEntity<Void> removeDependency(
            @PathVariable Long ticketId,
            @PathVariable Long blockerId,
            @AuthenticationPrincipal UserDetails userDetails) {
        ticketDependencyService.removeDependency(ticketId, blockerId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
