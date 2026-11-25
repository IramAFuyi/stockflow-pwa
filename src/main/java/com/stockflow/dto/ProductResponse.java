
package com.stockflow.dto;

import com.stockflow.model.Product;

public record ProductResponse(
    Long id,
    String sku,
    String name,
    String description,
    int stock,
    int minStock,
    Long categoryId,
    String categoryName,
    boolean active,
    boolean lowStock,
    Long version
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getStock(),
            product.getMinStock(),
            product.getCategory().getId(),
            product.getCategory().getName(),
            product.isActive(),
            product.isLowStock(),
            product.getVersion()
        );
    }
}
