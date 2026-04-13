package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.Offer;
import com.atheryon.mortgages.domain.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByApplicationId(UUID applicationId);

    List<Offer> findByOfferStatusAndExpiryDateBefore(OfferStatus status, LocalDate date);
}
