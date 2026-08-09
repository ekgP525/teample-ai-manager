package com.teample.dto.dashboard;

import lombok.Data;

@Data
public class TodoProgressUpdateRequest {
    private Boolean completed;
    private String status;
}