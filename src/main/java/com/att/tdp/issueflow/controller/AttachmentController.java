package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.response.AttachmentResponse;
import com.att.tdp.issueflow.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(attachmentService.uploadAttachment(ticketId, file, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<AttachmentResponse>> getAttachments(@PathVariable Long ticketId) {
        return ResponseEntity.ok(attachmentService.getAttachments(ticketId));
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        attachmentService.deleteAttachment(ticketId, attachmentId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
