package com.teample.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoItem {
    private Integer sourceIndex;
    public TodoItem(String name, String task, String deadline) {
        this(null, name, task, deadline);
    }
    @jakarta.validation.constraints.Size(max = 255)
    private String name;
    @jakarta.validation.constraints.Size(max = 4000)
    @jakarta.validation.constraints.NotBlank
    private String task;
    @jakarta.validation.constraints.Size(max = 255)
    private String deadline;
}
