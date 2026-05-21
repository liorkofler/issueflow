package com.att.tdp.issueflow.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String authorUsername,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MentionedUserDto> mentionedUsers
) {}
