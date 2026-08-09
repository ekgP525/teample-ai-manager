package com.teample.service;

import com.teample.dto.ProjectTodoResponse;
import com.teample.entity.*;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.IntegratedTodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectTodoServiceTest {

    private IntegratedTodoRepository todoRepository;
    private ProjectRepository projectRepository;
    private MinutesRepository minutesRepository;
    private ProjectTodoService service;

    @BeforeEach
    void setUp() {
        todoRepository = mock(IntegratedTodoRepository.class);
        projectRepository = mock(ProjectRepository.class);
        minutesRepository = mock(MinutesRepository.class);
        service = new ProjectTodoService(todoRepository, projectRepository, minutesRepository);
    }

    @Test
    void synchronizesMeetingTodosWithoutChangingMinutesJson() {
        Project project = Project.builder().id("project-id").build();
        List<TodoData> source = List.of(
                new TodoData("홍길동", "발표 자료 초안 작성", "2026-08-12"),
                new TodoData("김철수", "참고 자료 조사", "미정")
        );
        Minutes minutes = Minutes.builder()
                .id("minutes-id")
                .project(project)
                .todos(source)
                .build();
        when(todoRepository.findByMinutesIdOrderBySourceIndexAsc("minutes-id")).thenReturn(List.of());
        when(todoRepository.findMaxPriorityOrderByProjectId("project-id")).thenReturn(3);

        service.synchronizeFromMinutes(minutes);

        ArgumentCaptor<List<IntegratedTodo>> captor = ArgumentCaptor.forClass(List.class);
        verify(todoRepository).saveAll(captor.capture());
        List<IntegratedTodo> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getAssigneeName()).isEqualTo("홍길동");
        assertThat(saved.get(0).getContent()).isEqualTo("발표 자료 초안 작성");
        assertThat(saved.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(saved.get(0).getPriorityOrder()).isEqualTo(4);
        assertThat(saved.get(1).getDueDate()).isNull();
        assertThat(minutes.getTodos()).isSameAs(source);
    }

    @Test
    void synchronizationPreservesCompletedStatusAndAvoidsDuplicateRows() {
        Project project = Project.builder().id("project-id").build();
        Minutes minutes = Minutes.builder()
                .id("minutes-id")
                .project(project)
                .todos(List.of(new TodoData("홍길동", "수정된 업무", "2026-08-13")))
                .build();
        IntegratedTodo existing = IntegratedTodo.builder()
                .id("todo-id")
                .project(project)
                .minutes(minutes)
                .sourceIndex(0)
                .content("기존 업무")
                .assigneeName("홍길동")
                .status(TodoStatus.COMPLETED)
                .priorityOrder(1)
                .build();
        when(todoRepository.findByMinutesIdOrderBySourceIndexAsc("minutes-id"))
                .thenReturn(List.of(existing));

        service.synchronizeFromMinutes(minutes);

        ArgumentCaptor<List<IntegratedTodo>> captor = ArgumentCaptor.forClass(List.class);
        verify(todoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(existing);
        assertThat(existing.getContent()).isEqualTo("수정된 업무");
        assertThat(existing.getStatus()).isEqualTo(TodoStatus.COMPLETED);
    }

    @Test
    void completingAndRestoringTodoOnlyChangesStatus() {
        IntegratedTodo todo = IntegratedTodo.builder()
                .id("todo-id")
                .project(Project.builder().id("project-id").build())
                .content("업무")
                .assigneeName("담당자")
                .status(TodoStatus.TODO)
                .priorityOrder(1)
                .build();
        when(todoRepository.findByIdAndProjectId("todo-id", "project-id"))
                .thenReturn(Optional.of(todo));

        ProjectTodoResponse completed = service.updateStatus(
                "project-id", "todo-id", TodoStatus.COMPLETED);
        assertThat(completed.getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();

        ProjectTodoResponse restored = service.updateStatus(
                "project-id", "todo-id", TodoStatus.TODO);
        assertThat(restored.getStatus()).isEqualTo(TodoStatus.TODO);
        assertThat(restored.getCompletedAt()).isNull();
        verify(todoRepository, never()).delete(any());
    }

    @Test
    void reorderPersistsPriorityOrderForEveryActiveTodo() {
        Project project = Project.builder().id("project-id").build();
        IntegratedTodo first = IntegratedTodo.builder().id("first").project(project)
                .content("first").assigneeName("A").status(TodoStatus.TODO).priorityOrder(1).build();
        IntegratedTodo second = IntegratedTodo.builder().id("second").project(project)
                .content("second").assigneeName("B").status(TodoStatus.TODO).priorityOrder(2).build();
        when(todoRepository.findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc(
                "project-id", TodoStatus.TODO)).thenReturn(List.of(first, second));

        List<ProjectTodoResponse> reordered = service.reorder(
                "project-id", List.of("second", "first"));

        assertThat(reordered).extracting(ProjectTodoResponse::getId)
                .containsExactly("second", "first");
        assertThat(second.getPriorityOrder()).isEqualTo(1);
        assertThat(first.getPriorityOrder()).isEqualTo(2);
    }
}
