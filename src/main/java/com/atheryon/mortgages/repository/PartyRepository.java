package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartyRepository extends JpaRepository<Party, UUID> {

    Optional<Party> findByEmail(String email);
}
