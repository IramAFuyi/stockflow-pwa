
package com.stockflow.service;

import com.stockflow.dto.ProductResponse;
import com.stockflow.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> findAll() {
        return productRepository.findByActiveTrue()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .filter(product -> product.isActive())
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no encontrado"
                ));
    }

    public List<ProductResponse> findLowStock() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    public List<ProductResponse> findByCategory(Long categoryId) {
        return productRepository
                .findByCategoryIdAndActiveTrue(categoryId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }
}
