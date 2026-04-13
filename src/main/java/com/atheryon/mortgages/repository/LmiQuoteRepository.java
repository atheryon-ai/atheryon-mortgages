package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.LmiQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LmiQuoteRepository extends JpaRepository<LmiQuote, UUID> {
}
