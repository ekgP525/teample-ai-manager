package com.teample.service;

import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;
import com.teample.entity.ProjectTodo;
import com.teample.entity.TodoData;
import com.teample.entity.TodoMemberProgress;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectTodoRepository;
import com.teample.repository.TodoMemberProgressRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodoProgressSyncServiceTest {

    @Test
    void allAssigneeUsesProjectMemberRowsBeforeLegacyMembers() {
        MinutesRepository minutesRepository = mock(MinutesRepository.class);
        ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
        ProjectTodoRepository projectTodoRepository = mock(ProjectTodoRepository.class);
        TodoMemberProgressRepository todoMemberProgressRepository = mock(TodoMemberProgressRepository.class);
        TodoProgressSyncService service = new TodoProgressSyncService(
                minutesRepository,
                projectMemberRepository,
                projectTodoRepository,
                todoMemberProgressRepository
        );
        Project project = Project.builder()
                .id("project-id")
                .name("project")
                .members(List.of("legacy"))
                .build();
        Minutes minutes = Minutes.builder()
                .id("minutes-id")
                .project(project)
                .meetingDate(LocalDate.now())
                .todos(List.of(new TodoData("all", "Share update", "2099-01-01")))
                .build();
        ProjectTodo savedTodo = ProjectTodo.builder()
                .id("todo-id")
                .project(project)
                .minutes(minutes)
                .sourceIndex(0)
                .build();
        when(projectMemberRepository.findByProjectIdOrderByJoinedAtAsc("project-id"))
                .thenReturn(List.of(
                        member(project, "user-a", "Alice"),
                        member(project, "user-b", "Bob")
                ));
        when(projectTodoRepository.findByProjectIdAndMinutesIdAndSourceIndex("project-id", "minutes-id", 0))
                .thenReturn(Optional.empty());
        when(projectTodoRepository.save(any(ProjectTodo.class))).thenReturn(savedTodo);
        when(todoMemberProgressRepository.findByTodoId("todo-id")).thenReturn(List.of());
        ArgumentCaptor<TodoMemberProgress> progressCaptor = ArgumentCaptor.forClass(TodoMemberProgress.class);
        when(todoMemberProgressRepository.save(progressCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncMinutes(project, minutes);

        assertThat(progressCaptor.getAllValues())
                .extracting(TodoMemberProgress::getMemberName)
                .containsExactly("Alice", "Bob");
        assertThat(progressCaptor.getAllValues())
                .extracting(TodoMemberProgress::getUserId)
                .containsExactly("Alice", "Bob");
    }

    private ProjectMember member(Project project, String userId, String displayName) {
        return ProjectMember.builder()
                .project(project)
                .userId(userId)
                .displayName(displayName)
                .role(ProjectMemberRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
    }
}
