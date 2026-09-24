package com.teample.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MinutesRequest {

    @jakarta.validation.constraints.Size(max = 255)
    private String title;

    @NotBlank
    @jakarta.validation.constraints.Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String meetingDate;

    @NotBlank
    @jakarta.validation.constraints.Size(max = 50000)
    private String rawText;
}
