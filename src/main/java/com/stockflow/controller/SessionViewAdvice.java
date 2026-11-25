package com.stockflow.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {
        DashboardController.class,
        MovementViewController.class
})
public class SessionViewAdvice {

    @ModelAttribute("stockflowAccount")
    public String stockflowAccount(Principal principal) {

        return principal.getName();
    }
}