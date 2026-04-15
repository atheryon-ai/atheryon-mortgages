package com.atheryon.mortgages.migration.repository;

import com.atheryon.mortgages.migration.entity.ReconciliationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, UUID> {

    List<ReconciliationReport> findByJobId(UUID jobId);
}
