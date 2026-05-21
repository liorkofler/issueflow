package com.att.tdp.issueflow.dto.response;

public record AttachmentResponse(
        Long id,
        Long ticketId,
        String filename,
        String contentType,
        Long fileSize
) {
}
