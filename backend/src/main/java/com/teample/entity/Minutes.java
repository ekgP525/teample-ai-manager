package com.teample.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "minutes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Minutes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "raw_text", columnDefinition = "text", nullable = false)
    private String rawText;

    @Builder.Default
    @Column(name = "next_todo_index", nullable = false)
    private int nextTodoIndex = 0;

    private String title;

    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<String> discussions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<String> decisions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<String> pending;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<TodoData> todos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "next_agenda")
    private List<String> nextAgenda;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private EvidenceData evidence;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
