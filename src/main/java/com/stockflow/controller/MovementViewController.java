
package com.stockflow.controller;

import com.stockflow.dto.MovementResponse;
import com.stockflow.model.MovementType;
import com.stockflow.repository.DepartmentRepository;
import com.stockflow.service.ProductService;
import com.stockflow.service.StockMovementService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class MovementViewController {

    private final StockMovementService movementService;
    private final ProductService productService;
    private final DepartmentRepository departmentRepository;

    public MovementViewController(
            StockMovementService movementService,
            ProductService productService,
            DepartmentRepository departmentRepository) {

        this.movementService = movementService;
        this.productService = productService;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping("/movements")
    public String movements(Model model) {

        List<MovementResponse> movements =
                movementService.findAll();

        long totalIn = movements.stream()
                .filter(m -> m.type() == MovementType.IN)
                .mapToLong(MovementResponse::quantity)
                .sum();

        long totalOut = movements.stream()
                .filter(m -> m.type() == MovementType.OUT)
                .mapToLong(MovementResponse::quantity)
                .sum();

        model.addAttribute("movements", movements);
        model.addAttribute("products", productService.findAll());

        model.addAttribute(
            "departments", departmentRepository.findAll()
        );

        model.addAttribute("totalMovements", movements.size());
        model.addAttribute("totalIn", totalIn);
        model.addAttribute("totalOut", totalOut);

        return "movements";
    }
}
