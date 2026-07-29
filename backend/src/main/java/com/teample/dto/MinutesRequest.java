package com.teample.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MinutesRequest {

    private String title;

    @NotBlank
    private String meetingDate;

    @NotBlank
    private String rawText;
}
