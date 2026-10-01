package com.teample.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 회의록 JSON 안의 업무 한 건. {@code id}는 생성 시 부여되는 고정 식별자로,
 * 편집으로 순서가 바뀌거나 항목이 지워져도 통합 업무·진행률 행이 따라가도록 한다.
 */
@Data
@NoArgsConstructor
public class TodoData {
    private String id;
    private String name;
    private String task;
    private String deadline;

    public TodoData(String name, String task, String deadline) {
        this(null, name, task, deadline);
    }

    public TodoData(String id, String name, String task, String deadline) {
        this.id = id;
        this.name = name;
        this.task = task;
        this.deadline = deadline;
    }
}
