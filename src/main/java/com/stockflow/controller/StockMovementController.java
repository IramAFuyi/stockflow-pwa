
package com.stockflow.controller;

import com.stockflow.dto.MovementRequest;
import com.stockflow.dto.MovementResponse;
import com.stockflow.service.StockMovementService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @Valid @RequestBody MovementRequest request) {

        return movementService.create(request);
    }
}
