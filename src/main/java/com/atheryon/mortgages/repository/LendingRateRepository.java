package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.LendingRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LendingRateRepository extends JpaRepository<LendingRate, UUID> {

    List<LendingRate> findByProductId(UUID productId);
}
