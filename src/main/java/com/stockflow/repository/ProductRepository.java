
package com.stockflow.repository;

import com.stockflow.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    List<Product> findByActiveTrue();

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);

    long countByActiveTrue();

    @Query("""
        SELECT p FROM Product p
        WHERE p.stock <= p.minStock
        AND p.active = true
    """)
    List<Product> findLowStockProducts();
}
