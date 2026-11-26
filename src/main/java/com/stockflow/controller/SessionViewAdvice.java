
package com.stockflow.controller;

import java.security.Principal;

import org.springframework.security.core.Authentication;
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

    @ModelAttribute("stockflowIsAdmin")
    public boolean stockflowIsAdmin(
            Authentication authentication) {

        return authentication != null &&
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(
                                authority.getAuthority()));
    }

    @ModelAttribute("stockflowRole")
    public String stockflowRole(
            Authentication authentication) {

        return stockflowIsAdmin(authentication)
                ? "Administrador"
                : "Operador";
    }
}
