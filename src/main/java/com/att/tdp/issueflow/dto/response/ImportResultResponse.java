package com.att.tdp.issueflow.dto.response;

import java.util.List;

public record ImportResultResponse(
        int created,
        int failed,
        List<String> errors
) {
}
