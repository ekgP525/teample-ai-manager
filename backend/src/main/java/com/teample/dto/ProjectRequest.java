package com.teample.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ProjectRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<String> members;
}
