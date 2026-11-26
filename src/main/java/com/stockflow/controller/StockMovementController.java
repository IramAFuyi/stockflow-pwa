
package com.stockflow.controller;

import com.stockflow.dto.MovementRequest;
import com.stockflow.dto.MovementResponse;
import com.stockflow.service.StockMovementService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/movements")
public class StockMovementController {

    private final StockMovementService movementService;

    public StockMovementController(
            StockMovementService movementService) {

        this.movementService = movementService;
    }

    @GetMapping
    public List<MovementResponse> findAll() {
        return movementService.findAll();
    }

    @PostMapping
    public MovementResponse create(
            @Valid @RequestBody MovementRequest request,
            Principal principal) {

        return movementService.create(
                request,
                principal.getName());

    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleMovementError(
            ResponseStatusException exception) {

        Set<String> allowedMessages = Set.of(

                "Producto no encontrado",
                "El producto esta desactivado",
                "La cantidad debe ser mayor que cero",
                "El departamento no existe",
                "Indica el departamento receptor",
                "Las coordenadas deben enviarse juntas",
                "Existencias insuficientes",
                "Se excede el limite de existencias",
                "El identificador pertenece a otra operacion",
                "Usuario no autorizado");

        String reason = exception.getReason();

        String message = reason != null &&
                allowedMessages.contains(reason)

                        ? reason

                        : "No se pudo procesar el movimiento";

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of("message", message));
    }

}
