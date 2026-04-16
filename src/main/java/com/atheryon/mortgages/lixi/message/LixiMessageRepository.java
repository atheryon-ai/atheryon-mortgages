package com.atheryon.mortgages.lixi.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LixiMessageRepository extends JpaRepository<LixiMessage, UUID> {
    List<LixiMessage> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
