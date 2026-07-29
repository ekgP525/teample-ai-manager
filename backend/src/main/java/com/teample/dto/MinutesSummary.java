package com.teample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesSummary {
    private String id;
    private String subject;
    private String meetingDate;
    private String topic;
    private String createdAt;
}
