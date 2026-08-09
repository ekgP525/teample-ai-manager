package com.teample.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TodoReorderRequest {
    @NotEmpty
    private List<String> orderedTodoIds;
}
