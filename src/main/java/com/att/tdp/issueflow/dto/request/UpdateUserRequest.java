package com.att.tdp.issueflow.dto.request;

import com.att.tdp.issueflow.entity.UserRole;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String fullName;
    private UserRole role;
}
