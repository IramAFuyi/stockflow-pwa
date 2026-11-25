
package com.stockflow.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(
    assignableTypes = {
        ProductController.class,
        StockMovementController.class
    }
)
public class ProductExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleBusinessError(
            ResponseStatusException ex) {

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                    "message",
                    ex.getReason() != null
                        ? ex.getReason()
                        : "Operacion no permitida"
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(
            MethodArgumentNotValidException ex) {

        var error = ex.getBindingResult().getFieldError();

        String message = error != null
                ? error.getField() + ": "
                    + error.getDefaultMessage()
                : "Datos invalidos";

        return ResponseEntity.badRequest()
                .body(Map.of("message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleIntegrityError(
            DataIntegrityViolationException ex) {

        return ResponseEntity.status(409)
                .body(Map.of(
                    "message",
                    "Los datos entran en conflicto con un registro existente"
                ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> handleConcurrencyError(
            OptimisticLockingFailureException ex) {

        return ResponseEntity.status(409)
                .body(Map.of(
                    "message",
                    "El producto cambio. Recarga la informacion."
                ));
    }
}
