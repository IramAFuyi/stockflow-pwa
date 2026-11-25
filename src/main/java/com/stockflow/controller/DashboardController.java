
package com.stockflow.controller;

import com.stockflow.dto.ProductResponse;
import com.stockflow.service.ProductService;
import com.stockflow.service.CategoryService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public DashboardController(
            ProductService productService,
            CategoryService categoryService) {

        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {

        List<ProductResponse> products =
                productService.findAll();

        var categories = categoryService.findAll();

        long totalUnits = products.stream()
                .mapToLong(ProductResponse::stock)
                .sum();

        long lowStock = products.stream()
                .filter(ProductResponse::lowStock)
                .count();

        long outOfStock = products.stream()
                .filter(product -> product.stock() == 0)
                .count();

        model.addAttribute("products", products);
        model.addAttribute("categories", categories);
        model.addAttribute("totalProducts", products.size());
        model.addAttribute("totalUnits", totalUnits);
        model.addAttribute("totalCategories", categories.size());
        model.addAttribute("lowStock", lowStock);
        model.addAttribute("outOfStock", outOfStock);

        return "dashboard";
    }
}
