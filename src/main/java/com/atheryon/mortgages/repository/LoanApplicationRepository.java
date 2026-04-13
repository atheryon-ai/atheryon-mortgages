package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    @Query("SELECT a FROM LoanApplication a LEFT JOIN FETCH a.product LEFT JOIN FETCH a.applicationParties ap LEFT JOIN FETCH ap.party WHERE a.id = :id")
    Optional<LoanApplication> findByIdWithDetails(UUID id);

    Optional<LoanApplication> findByApplicationNumber(String num);

    List<LoanApplication> findByStatus(ApplicationStatus status);

    List<LoanApplication> findByStatusAndAssignedTo(ApplicationStatus status, String assignedTo);

    List<LoanApplication> findByAssignedTo(String assignedTo);

    Page<LoanApplication> findByStatusIn(List<ApplicationStatus> statuses, Pageable pageable);
}
