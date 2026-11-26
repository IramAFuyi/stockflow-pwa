
package com.stockflow.dto;

import com.stockflow.model.StockMovement;
import com.stockflow.model.MovementType;

import java.time.Instant;
import java.util.UUID;

public record MovementResponse(

        Long id,
        UUID operationUuid,
        Long productId,
        String productName,
        String sku,
        Long departmentId,
        String departmentName,
        MovementType type,
        int quantity,
        int previousStock,
        int resultingStock,
        String observations,
        Double latitude,
        Double longitude,
        Instant createdAt,
        Long userId,
        String registeredBy

) {

    public static MovementResponse from(
            StockMovement movement) {

        var product = movement.getProduct();
        var department = movement.getDepartment();
        var user = movement.getUser();

        return new MovementResponse(
                movement.getId(),
                movement.getOperationUuid(),
                product.getId(),
                product.getName(),
                product.getSku(),
                department != null
                        ? department.getId()
                        : null,
                department != null
                        ? department.getName()
                        : null,
                movement.getType(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getResultingStock(),
                movement.getObservations(),
                movement.getLatitude(),
                movement.getLongitude(),
                movement.getCreatedAt(),
                user != null
                        ? user.getId()
                        : null,
                user != null
                        ? user.getUsername()
                        : null);
    }
}
