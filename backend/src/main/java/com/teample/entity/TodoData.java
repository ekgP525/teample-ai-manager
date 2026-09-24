package com.teample.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoData {
    private Integer sourceIndex;
    public TodoData(String name, String task, String deadline) {
        this(null, name, task, deadline);
    }
    private String name;
    private String task;
    private String deadline;
}
