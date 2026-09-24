package com.teample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesResponse {
    private String id;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String title;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String topic;
    @jakarta.validation.constraints.Size(max = 100)
    private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 4000) String> discussions;
    @jakarta.validation.constraints.Size(max = 100)
    private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 4000) String> decisions;
    @jakarta.validation.constraints.Size(max = 100)
    private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 4000) String> pending;
    @jakarta.validation.Valid
    @jakarta.validation.constraints.Size(max = 100)
    private List<@jakarta.validation.constraints.NotNull TodoItem> todos;
    @jakarta.validation.constraints.Size(max = 100)
    private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 4000) String> nextAgenda;
    private MinutesEvidence evidence;
}
