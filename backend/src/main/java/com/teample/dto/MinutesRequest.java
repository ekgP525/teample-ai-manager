package com.teample.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MinutesRequest {

    @NotBlank
    private String subject;

    @NotBlank
    private String meetingDate;

    @NotEmpty
    private List<String> members;

    @NotBlank
    private String rawText;
}
