package com.teample.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TodoItem {
    /** 회의록 안에서 업무를 고정 식별하는 값. 편집 시 그대로 돌려보내야 진행 상태가 유지된다. 새 항목은 null. */
    private String id;
    private String name;
    private String task;
    private String deadline;

    public TodoItem(String name, String task, String deadline) {
        this(null, name, task, deadline);
    }

    public TodoItem(String id, String name, String task, String deadline) {
        this.id = id;
        this.name = name;
        this.task = task;
        this.deadline = deadline;
    }
}
