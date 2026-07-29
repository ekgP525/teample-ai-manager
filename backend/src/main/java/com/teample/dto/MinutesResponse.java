package com.teample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesResponse {
    private String id;
    private String topic;
    private List<String> discussions;
    private List<String> decisions;
    private List<String> pending;
    private List<TodoItem> todos;
    private List<String> nextAgenda;
}
