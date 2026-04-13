package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.PropertySecurity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PropertySecurityRepository extends JpaRepository<PropertySecurity, UUID> {
}
