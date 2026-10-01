package com.teample.service;

import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.repository.MinutesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회의록 저장과 업무 동기화를 한 트랜잭션으로 묶는다.
 * Claude 호출은 이 바깥에서 끝내고 결과만 넘기므로 AI 응답을 기다리는 동안 DB 커넥션을 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MinutesStore {

    private final MinutesRepository minutesRepository;
    private final ProjectTodoService projectTodoService;
    private final TodoProgressSyncService todoProgressSyncService;

    @Transactional
    public Minutes saveAndSync(Project project, Minutes minutes) {
        Minutes saved = minutesRepository.save(minutes);
        projectTodoService.synchronizeFromMinutes(saved);
        todoProgressSyncService.syncMinutes(project, saved);
        return saved;
    }
}
