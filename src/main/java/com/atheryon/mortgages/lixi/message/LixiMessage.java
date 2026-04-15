package com.atheryon.mortgages.lixi.message;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lixi_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LixiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID applicationId;

    @Column(nullable = false)
    private String direction;

    @Column(nullable = false)
    private String standard;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String format;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(columnDefinition = "jsonb")
    private String validationResult;

    private String senderId;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
