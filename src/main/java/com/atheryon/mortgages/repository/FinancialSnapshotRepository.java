package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, UUID> {

    Optional<FinancialSnapshot> findByApplicationId(UUID applicationId);
}
