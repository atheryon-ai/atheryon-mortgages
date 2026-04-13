package com.atheryon.mortgages.config;

import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.enums.ProductType;
import com.atheryon.mortgages.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
@Profile("dev")
public class DevDataSeeder {

    @Bean
    CommandLineRunner seedProducts(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                productRepository.save(Product.builder()
                        .productType(ProductType.STANDARD_VARIABLE)
                        .name("Atheryon Standard Variable")
                        .brand("Atheryon")
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .minimumLoanAmount(new BigDecimal("50000"))
                        .maximumLoanAmount(new BigDecimal("5000000"))
                        .minimumTermMonths(12)
                        .maximumTermMonths(360)
                        .maximumLtv(new BigDecimal("0.95"))
                        .build());

                productRepository.save(Product.builder()
                        .productType(ProductType.FIXED_RATE)
                        .name("Atheryon Fixed 2yr")
                        .brand("Atheryon")
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .minimumLoanAmount(new BigDecimal("100000"))
                        .maximumLoanAmount(new BigDecimal("3000000"))
                        .minimumTermMonths(24)
                        .maximumTermMonths(360)
                        .maximumLtv(new BigDecimal("0.90"))
                        .build());

                productRepository.save(Product.builder()
                        .productType(ProductType.BASIC_VARIABLE)
                        .name("Atheryon First Home Buyer Special")
                        .brand("Atheryon")
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .minimumLoanAmount(new BigDecimal("200000"))
                        .maximumLoanAmount(new BigDecimal("1500000"))
                        .minimumTermMonths(240)
                        .maximumTermMonths(360)
                        .maximumLtv(new BigDecimal("0.95"))
                        .build());
            }
        };
    }
}
