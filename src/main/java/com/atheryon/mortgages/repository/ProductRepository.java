package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByProductType(ProductType type);

    List<Product> findByEffectiveToIsNullOrEffectiveToAfter(LocalDate date);
}
