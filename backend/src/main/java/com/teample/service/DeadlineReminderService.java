package com.teample.service;

import com.teample.entity.IntegratedTodo;
import com.teample.entity.KakaoLink;
import com.teample.entity.NotificationLog;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.TodoStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.KakaoLinkRepository;
import com.teample.repository.NotificationLogRepository;
import com.teample.repository.ProjectMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 카카오를 연결한 사용자에게 오늘·내일 마감인 업무를 하루 한 번 카카오톡으로 알려준다.
 */
@Service
public class DeadlineReminderService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);

    private final KakaoLinkRepository kakaoLinkRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IntegratedTodoRepository integratedTodoRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final KakaoLinkService kakaoLinkService;
    private final String appBaseUrl;

    public DeadlineReminderService(
            KakaoLinkRepository kakaoLinkRepository,
            ProjectMemberRepository projectMemberRepository,
            IntegratedTodoRepository integratedTodoRepository,
            NotificationLogRepository notificationLogRepository,
            KakaoLinkService kakaoLinkService,
            @Value("${app.frontend-base-url:http://localhost:3000}") String appBaseUrl
    ) {
        this.kakaoLinkRepository = kakaoLinkRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.integratedTodoRepository = integratedTodoRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.kakaoLinkService = kakaoLinkService;
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    /** 알림을 켠 모든 사용자에게 오늘 분 알림을 보낸다. 보낸 사용자 수를 돌려준다. */
    @Transactional
    public int sendDueReminders(LocalDate today) {
        int sent = 0;
        for (KakaoLink link : kakaoLinkRepository.findByDeadlineRemindersTrue()) {
            String dedupeKey = "deadline:" + today;
            if (notificationLogRepository.existsByUserIdAndChannelAndDedupeKey(
                    link.getUserId(), NotificationLog.CHANNEL_KAKAO, dedupeKey)) {
                continue;
            }
            List<DueTodo> dueTodos = findDueTodos(link.getUserId(), today);
            if (dueTodos.isEmpty()) {
                continue;
            }
            try {
                kakaoLinkService.sendToSelf(link.getUserId(), buildMessage(dueTodos, today),
                        appBaseUrl + "/dashboard", "업무 확인하기");
                notificationLogRepository.save(NotificationLog.builder()
                        .userId(link.getUserId())
                        .channel(NotificationLog.CHANNEL_KAKAO)
                        .dedupeKey(dedupeKey)
                        .sentAt(LocalDateTime.now())
                        .build());
                sent++;
            } catch (RuntimeException e) {
                log.warn("카카오 마감 알림 발송 실패 (user={}): {}", link.getUserId(), e.getMessage());
            }
        }
        return sent;
    }

    /** 사용자가 속한 프로젝트에서 본인 이름으로 배정된, 오늘 또는 내일 마감인 미완료 업무. */
    List<DueTodo> findDueTodos(String userId, LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        List<DueTodo> result = new ArrayList<>();
        for (ProjectMember membership : projectMemberRepository.findByUserIdOrderByJoinedAtAsc(userId)) {
            Project project = membership.getProject();
            if (project == null || project.getId() == null || !project.isVisibleInActiveList()) {
                continue;
            }
            String displayName = normalize(membership.getDisplayName());
            for (IntegratedTodo todo : integratedTodoRepository
                    .findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc(project.getId(), TodoStatus.TODO)) {
                LocalDate due = todo.getDueDate();
                if (due == null || due.isBefore(today) || due.isAfter(tomorrow)) {
                    continue;
                }
                if (!isAssignedTo(todo.getAssigneeName(), displayName)) {
                    continue;
                }
                result.add(new DueTodo(project.getName(), todo.getContent(), due));
            }
        }
        result.sort((a, b) -> a.dueDate().compareTo(b.dueDate()));
        return result;
    }

    String buildMessage(List<DueTodo> dueTodos, LocalDate today) {
        StringBuilder builder = new StringBuilder("[팀플 AI] 마감이 다가온 업무가 있어요.\n");
        int shown = 0;
        for (DueTodo todo : dueTodos) {
            if (shown == 4) {
                builder.append("외 ").append(dueTodos.size() - shown).append("건\n");
                break;
            }
            String when = todo.dueDate().equals(today) ? "오늘" : "내일";
            builder.append("• ").append(when).append(" ").append(todo.dueDate().format(DATE_LABEL))
                    .append(" · ").append(todo.projectName()).append(" · ").append(todo.content()).append('\n');
            shown++;
        }
        return builder.toString().trim();
    }

    private static boolean isAssignedTo(String assigneeName, String displayName) {
        if (assigneeName == null || displayName == null) {
            return false;
        }
        String normalizedAssignee = normalize(assigneeName);
        if (normalizedAssignee == null) {
            return false;
        }
        if (normalizedAssignee.equalsIgnoreCase(displayName)) {
            return true;
        }
        for (String token : normalizedAssignee.split("[,/;|&]+")) {
            String candidate = normalize(token);
            if (candidate != null && (candidate.equalsIgnoreCase(displayName)
                    || candidate.equalsIgnoreCase("전체") || candidate.equalsIgnoreCase("all"))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record DueTodo(String projectName, String content, LocalDate dueDate) {
    }
}
