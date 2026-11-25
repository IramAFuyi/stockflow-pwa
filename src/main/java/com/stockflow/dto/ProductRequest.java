
package com.stockflow.dto;

import jakarta.validation.constraints.*;

public record ProductRequest(

    @NotBlank(message = "El SKU es obligatorio")
    @Size(max = 50)
    String sku,

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150)
    String name,

    @Size(max = 500)
    String description,

    @NotNull(message = "El stock minimo es obligatorio")
    @PositiveOrZero
    Integer minStock,

    @NotNull(message = "La categoria es obligatoria")
    Long categoryId,

    Long version

) {
}
