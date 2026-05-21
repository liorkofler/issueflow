package com.att.tdp.issueflow.dto.response;

import java.util.List;

public record MentionsPageResponse(
        List<CommentResponse> data,
        long total,
        int page
) {}
