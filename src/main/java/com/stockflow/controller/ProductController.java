
package com.stockflow.controller;

import com.stockflow.dto.ProductResponse;
import com.stockflow.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @GetMapping("/low-stock")
    public List<ProductResponse> findLowStock() {
        return productService.findLowStock();
    }

    @GetMapping("/category/{categoryId}")
    public List<ProductResponse> findByCategory(
            @PathVariable Long categoryId) {
        return productService.findByCategory(categoryId);
    }
}
