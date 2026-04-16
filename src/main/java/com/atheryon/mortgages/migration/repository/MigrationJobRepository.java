package com.atheryon.mortgages.migration.repository;

import com.atheryon.mortgages.migration.entity.MigrationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MigrationJobRepository extends JpaRepository<MigrationJob, UUID> {

    List<MigrationJob> findByStatus(String status);

    List<MigrationJob> findBySourceFilename(String sourceFilename);

    List<MigrationJob> findAllByOrderByCreatedAtDesc();
}
