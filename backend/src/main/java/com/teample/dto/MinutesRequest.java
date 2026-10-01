package com.teample.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MinutesRequest {

    @Size(max = 255)
    private String title;

    @NotBlank
    private String meetingDate;

    @NotBlank
    @Size(max = 200_000)
    private String rawText;
}
