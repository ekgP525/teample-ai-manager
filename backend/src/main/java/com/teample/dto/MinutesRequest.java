package com.teample.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MinutesRequest {

    @NotBlank
    private String meetingDate;

    @NotBlank
    private String rawText;
}
