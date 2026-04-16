package com.atheryon.mortgages.migration.repository;

import com.atheryon.mortgages.migration.entity.RemediationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RemediationActionRepository extends JpaRepository<RemediationAction, UUID> {

    List<RemediationAction> findByJobId(UUID jobId);

    List<RemediationAction> findByJobIdAndRuleId(UUID jobId, String ruleId);
}
