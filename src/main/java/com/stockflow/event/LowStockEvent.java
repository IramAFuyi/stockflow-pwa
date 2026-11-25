package com.stockflow.event;

public record LowStockEvent(

        Long productId,
        String sku,
        String productName,
        int stock,
        int minStock

) {
}