package com.teample.service;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.dto.TodoItem;
import com.teample.entity.Minutes;
import com.teample.entity.TodoData;
import com.teample.repository.MinutesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinutesService {

    private final MinutesRepository minutesRepository;

    public MinutesResponse create(MinutesRequest request) {
        // TODO: Claude API 호출로 교체
        String topic = "중간 발표 PPT 역할 분담 및 일정 조율";
        List<String> discussions = List.of(
                "데이터 수집 완료 여부 확인 — 전원 완료",
                "발표 자료 제작 도구 논의 — Google Slides vs Canva",
                "발표자 선정 및 스크립트 작성 일정 논의",
                "다음 회의 일정 조율"
        );
        List<String> decisions = List.of(
                "발표 PPT는 Google Slides로 공동 작업",
                "발표자는 이다혜로 확정",
                "다음 회의는 수요일 오후 3시"
        );
        List<String> pending = List.of(
                "교수님 피드백 반영 여부 — 다음 회의에서 결정",
                "추가 설문조사 필요성 검토"
        );
        List<TodoData> todos = List.of(
                new TodoData("이다혜", "발표 스크립트 초안 작성", "7/23(수)"),
                new TodoData("박규남", "데이터 시각화 차트 3개 제작", "7/22(화)"),
                new TodoData("김다희", "PPT 디자인 템플릿 세팅", "7/22(화)")
        );
        List<String> nextAgenda = List.of(
                "PPT 1차 초안 리뷰",
                "발표 스크립트 피드백",
                "교수님 피드백 반영 여부 최종 결정"
        );

        Minutes minutes = Minutes.builder()
                .subject(request.getSubject())
                .meetingDate(LocalDate.parse(request.getMeetingDate()))
                .members(request.getMembers())
                .rawText(request.getRawText())
                .topic(topic)
                .discussions(discussions)
                .decisions(decisions)
                .pending(pending)
                .todos(todos)
                .nextAgenda(nextAgenda)
                .build();

        Minutes saved = minutesRepository.save(minutes);

        return MinutesResponse.builder()
                .id(saved.getId())
                .topic(saved.getTopic())
                .discussions(saved.getDiscussions())
                .decisions(saved.getDecisions())
                .pending(saved.getPending())
                .todos(saved.getTodos().stream()
                        .map(t -> new TodoItem(t.getName(), t.getTask(), t.getDeadline()))
                        .toList())
                .nextAgenda(saved.getNextAgenda())
                .build();
    }

    public Optional<MinutesResponse> findById(String id) {
        return minutesRepository.findById(id).map(this::toResponse);
    }

    public Optional<MinutesResponse> update(String id, MinutesResponse request) {
        return minutesRepository.findById(id).map(minutes -> {
            minutes.setTopic(request.getTopic());
            minutes.setDiscussions(new ArrayList<>(request.getDiscussions()));
            minutes.setDecisions(new ArrayList<>(request.getDecisions()));
            minutes.setPending(new ArrayList<>(request.getPending()));
            minutes.setTodos(request.getTodos().stream()
                    .map(t -> new TodoData(t.getName(), t.getTask(), t.getDeadline()))
                    .toList());
            minutes.setNextAgenda(new ArrayList<>(request.getNextAgenda()));
            Minutes saved = minutesRepository.save(minutes);
            return toResponse(saved);
        });
    }

    private MinutesResponse toResponse(Minutes m) {
        return MinutesResponse.builder()
                .id(m.getId())
                .topic(m.getTopic())
                .discussions(m.getDiscussions())
                .decisions(m.getDecisions())
                .pending(m.getPending())
                .todos(m.getTodos().stream()
                        .map(t -> new TodoItem(t.getName(), t.getTask(), t.getDeadline()))
                        .toList())
                .nextAgenda(m.getNextAgenda())
                .build();
    }
}
