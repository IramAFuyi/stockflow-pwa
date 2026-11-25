package com.stockflow.controller;

import com.stockflow.model.Department;
import com.stockflow.repository.DepartmentRepository;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(
            DepartmentRepository departmentRepository) {

        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public List<Department> findAll() {
        return departmentRepository.findAll();
    }
}