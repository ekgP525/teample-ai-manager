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

    @Column(nullable = false)
    private String subject;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<String> members;

    @Column(name = "raw_text", columnDefinition = "text", nullable = false)
    private String rawText;

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

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
