package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.config.SecurityConfig;
import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.enums.ProductType;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    private Product createTestProduct(UUID id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setProductType(ProductType.STANDARD_VARIABLE);
        product.setName(name);
        product.setBrand("Atheryon");
        product.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        product.setMaximumLtv(new BigDecimal("0.95"));
        product.setMinimumLoanAmount(new BigDecimal("50000"));
        product.setMaximumLoanAmount(new BigDecimal("5000000"));
        product.setMinimumTermMonths(12);
        product.setMaximumTermMonths(360);
        return product;
    }

    @Test
    void listActive_returnsProductList() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Product p1 = createTestProduct(id1, "Standard Variable");
        Product p2 = createTestProduct(id2, "Fixed Rate 2yr");

        when(productService.listActive()).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Standard Variable"))
                .andExpect(jsonPath("$[1].name").value("Fixed Rate 2yr"));
    }

    @Test
    void getById_returnsProduct() throws Exception {
        UUID id = UUID.randomUUID();
        Product product = createTestProduct(id, "Standard Variable");

        when(productService.getById(id)).thenReturn(product);

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Standard Variable"))
                .andExpect(jsonPath("$.productType").value("STANDARD_VARIABLE"))
                .andExpect(jsonPath("$.brand").value("Atheryon"));
    }

    @Test
    void getById_nonExistentId_returns404() throws Exception {
        UUID id = UUID.randomUUID();

        when(productService.getById(id)).thenThrow(
                new ResourceNotFoundException("Product", "id", id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
