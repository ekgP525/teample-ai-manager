package com.teample.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.time.LocalDate;

@Data
public class ProjectRequest {

    @NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String name;

    private List<String> members;

    @JsonAlias("disposalDeadline")
    private LocalDate endDate;
}
