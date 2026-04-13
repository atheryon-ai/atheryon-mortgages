package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, UUID> {

    List<WorkflowEvent> findByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}
