package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.DecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, UUID> {

    Optional<DecisionRecord> findByApplicationId(UUID applicationId);
}
