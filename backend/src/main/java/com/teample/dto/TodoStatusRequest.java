package com.teample.dto;

import com.teample.entity.TodoStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TodoStatusRequest {
    @NotNull
    private TodoStatus status;
}
