package com.teample.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceData {
    private String title;
    private String topic;
    private List<String> discussions = new ArrayList<>();
    private List<String> decisions = new ArrayList<>();
    private List<String> pending = new ArrayList<>();
    private List<String> todos = new ArrayList<>();
    private List<String> nextAgenda = new ArrayList<>();
}
