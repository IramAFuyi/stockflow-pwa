
package com.stockflow.dto;

import com.stockflow.model.MovementType;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record MovementRequest(

    @NotNull
    UUID operationUuid,

    @NotNull
    @Positive
    Long productId,

    @Positive
    Long departmentId,

    @NotNull
    MovementType type,

    @NotNull
    @Positive
    Integer quantity,

    @Size(max = 500)
    String observations,

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    Double latitude,

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    Double longitude

) {
}
