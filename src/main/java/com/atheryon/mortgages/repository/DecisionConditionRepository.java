package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.DecisionCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DecisionConditionRepository extends JpaRepository<DecisionCondition, UUID> {
}
