package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.dto.response.ProductResponse;
import com.atheryon.mortgages.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Mortgage product catalogue")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List all active products")
    public ResponseEntity<List<ProductResponse>> listActive() {
        List<Product> products = productService.listActive();
        List<ProductResponse> responses = products.stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        Product product = productService.getById(id);
        return ResponseEntity.ok(ProductResponse.from(product));
    }
}
