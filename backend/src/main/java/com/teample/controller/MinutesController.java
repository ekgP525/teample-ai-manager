package com.teample.controller;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.dto.TodoItem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MinutesController {

    @PostMapping("/minutes")
    public ResponseEntity<MinutesResponse> createMinutes(@Valid @RequestBody MinutesRequest request) {
        // TODO: Claude API 연동 — 지금은 더미 응답 반환
        MinutesResponse response = MinutesResponse.builder()
                .id(UUID.randomUUID().toString())
                .summary("중간 발표 PPT 역할 분담 및 다음 회의 일정을 논의함. "
                        + "데이터 수집은 완료되었으며, 발표 자료는 수요일까지 1차 완성 목표.")
                .decisions(List.of(
                        "발표 PPT는 Google Slides로 공동 작업",
                        "발표자는 이다혜로 확정",
                        "다음 회의는 수요일 오후 3시"
                ))
                .todos(List.of(
                        new TodoItem("이다혜", "발표 스크립트 초안 작성", "7/23(수)"),
                        new TodoItem("박규남", "데이터 시각화 차트 3개 제작", "7/22(화)"),
                        new TodoItem("김다희", "PPT 디자인 템플릿 세팅", "7/22(화)")
                ))
                .pending(List.of(
                        "교수님 피드백 반영 여부 — 다음 회의에서 결정",
                        "추가 설문조사 필요성 검토"
                ))
                .build();

        return ResponseEntity.ok(response);
    }
}
