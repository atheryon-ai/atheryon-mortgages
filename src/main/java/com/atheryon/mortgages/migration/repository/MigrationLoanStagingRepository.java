package com.atheryon.mortgages.migration.repository;

import com.atheryon.mortgages.migration.entity.MigrationLoanStaging;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MigrationLoanStagingRepository extends JpaRepository<MigrationLoanStaging, Long> {

    List<MigrationLoanStaging> findByJobId(UUID jobId);

    Page<MigrationLoanStaging> findByJobId(UUID jobId, Pageable pageable);

    List<MigrationLoanStaging> findByJobIdAndClassification(UUID jobId, String classification);

    long countByJobIdAndClassification(UUID jobId, String classification);

    long countByJobId(UUID jobId);

    long countByJobIdAndPromoted(UUID jobId, boolean promoted);
}
