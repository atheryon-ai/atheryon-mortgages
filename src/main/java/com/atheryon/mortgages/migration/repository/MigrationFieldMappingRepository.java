package com.atheryon.mortgages.migration.repository;

import com.atheryon.mortgages.migration.entity.MigrationFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MigrationFieldMappingRepository extends JpaRepository<MigrationFieldMapping, UUID> {

    List<MigrationFieldMapping> findByJobId(UUID jobId);

    List<MigrationFieldMapping> findByJobIdAndStatus(UUID jobId, String status);
}
