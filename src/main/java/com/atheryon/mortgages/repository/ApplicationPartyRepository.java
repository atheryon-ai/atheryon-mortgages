package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.ApplicationParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationPartyRepository extends JpaRepository<ApplicationParty, UUID> {

    List<ApplicationParty> findByApplicationId(UUID applicationId);
}
