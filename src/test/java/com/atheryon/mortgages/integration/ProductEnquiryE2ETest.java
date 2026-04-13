package com.atheryon.mortgages.integration;

import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.enums.ProductType;
import com.atheryon.mortgages.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for SRS Process 1 - Product Enquiry.
 *
 * Validates the product catalogue API endpoints that allow borrowers
 * and brokers to browse available mortgage products.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductEnquiryE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // Seed the three standard products that mirror the DevDataSeeder catalogue
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

    @Test
    void listProducts_returnsSeededProducts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode products = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(products.isArray()).isTrue();
        assertThat(products.size()).isGreaterThanOrEqualTo(3);

        // Verify each product has the required fields
        for (JsonNode product : products) {
            assertThat(product.has("id")).isTrue();
            assertThat(product.has("name")).isTrue();
            assertThat(product.has("productType")).isTrue();
            assertThat(product.has("brand")).isTrue();
            assertThat(product.has("effectiveFrom")).isTrue();
        }
    }

    @Test
    void getProductById_returnsDetails() throws Exception {
        // Create a specific product via repo and retrieve by ID
        Product saved = productRepository.save(Product.builder()
                .productType(ProductType.STANDARD_VARIABLE)
                .name("Test Product Detail")
                .brand("Atheryon")
                .effectiveFrom(LocalDate.now())
                .minimumLoanAmount(new BigDecimal("50000"))
                .maximumLoanAmount(new BigDecimal("5000000"))
                .minimumTermMonths(12)
                .maximumTermMonths(360)
                .maximumLtv(new BigDecimal("0.95"))
                .build());

        mockMvc.perform(get("/api/v1/products/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.name").value("Test Product Detail"))
                .andExpect(jsonPath("$.productType").value("STANDARD_VARIABLE"))
                .andExpect(jsonPath("$.brand").value("Atheryon"))
                .andExpect(jsonPath("$.effectiveFrom").isNotEmpty())
                .andExpect(jsonPath("$.maximumLtv").isNotEmpty())
                .andExpect(jsonPath("$.minimumLoanAmount").isNotEmpty())
                .andExpect(jsonPath("$.maximumLoanAmount").isNotEmpty());
    }

    @Test
    void getProductById_nonExistent_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/products/{id}", randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void listProducts_containsExpectedProductTypes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode products = objectMapper.readTree(result.getResponse().getContentAsString());

        List<String> productTypes = new java.util.ArrayList<>();
        for (JsonNode product : products) {
            productTypes.add(product.get("productType").asText());
        }

        assertThat(productTypes).contains(
                "STANDARD_VARIABLE",
                "FIXED_RATE",
                "BASIC_VARIABLE"
        );
    }
}
