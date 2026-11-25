
package com.stockflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class AccountBindingFilter
        extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        if (!path.startsWith("/api/")) {

            filterChain.doFilter(request, response);
            return;
        }

        String account = request.getHeader(
                "X-StockFlow-Account");

        boolean movementWrite = "POST".equals(request.getMethod())
                && ("/api/movements".equals(path)
                        || "/api/movements/".equals(path));

        // Los movimientos requieren identificar
        // la cuenta que genero la operacion.
        if (account == null || account.isBlank()) {

            if (movementWrite) {

                reject(response);
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        try {

            String decodedAccount = URLDecoder.decode(
                    account,
                    StandardCharsets.UTF_8);

            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !authentication.getName()
                            .equals(decodedAccount)) {

                reject(response);
                return;
            }

        } catch (IllegalArgumentException ex) {

            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response)
            throws IOException {

        response.setStatus(
                HttpServletResponse.SC_FORBIDDEN);

        response.setContentType(
                "application/json;charset=UTF-8");

        response.getWriter().write(
                "{\"message\":\"La cuenta no coincide "
                        + "con la sesion autenticada.\"}");
    }
}
