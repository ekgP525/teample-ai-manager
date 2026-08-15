package com.teample.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.time.LocalDate;

@Data
public class ProjectRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<String> members;

    @JsonAlias("disposalDeadline")
    private LocalDate endDate;
}
