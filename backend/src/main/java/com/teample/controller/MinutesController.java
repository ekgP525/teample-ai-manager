package com.teample.controller;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.service.MinutesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MinutesController {

    private final MinutesService minutesService;

    @PostMapping("/minutes")
    public ResponseEntity<MinutesResponse> createMinutes(@Valid @RequestBody MinutesRequest request) {
        MinutesResponse response = minutesService.create(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/minutes/{id}")
    public ResponseEntity<MinutesResponse> getMinutes(@PathVariable String id) {
        return minutesService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/minutes/{id}")
    public ResponseEntity<MinutesResponse> updateMinutes(
            @PathVariable String id,
            @RequestBody MinutesResponse request) {
        return minutesService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
