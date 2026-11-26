
package com.stockflow.config;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountBindingFilterTest {

    private AccountBindingFilter filter;

    private FilterChain chain;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {

        filter = new AccountBindingFilter();

        chain = mock(FilterChain.class);

        response = new MockHttpServletResponse();

        var authentication = new UsernamePasswordAuthenticationToken(
                "operador",
                null,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_OPERATOR")));

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {

        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(
            String method,
            String path,
            String account) {

        var request = new MockHttpServletRequest(
                method, path);

        request.setServletPath(path);

        if (account != null) {

            request.addHeader(
                    "X-StockFlow-Account",
                    account);
        }

        return request;
    }

    // CP-197

    @Test
    @DisplayName("Permitir movimiento de la cuenta correcta")
    void allowMatchingAccount() throws Exception {

        var request = request(
                "POST",
                "/api/movements",
                "operador");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);

        assertEquals(200, response.getStatus());
    }

    // CP-198

    @Test
    @DisplayName("Rechazar identidad diferente a la sesion")
    void rejectDifferentAccount() throws Exception {

        var request = request(
                "POST",
                "/api/movements",
                "admin");

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());

        verifyNoInteractions(chain);
    }

    // CP-199

    @Test
    @DisplayName("Rechazar movimiento sin encabezado de cuenta")
    void rejectMissingAccount() throws Exception {

        var request = request(
                "POST",
                "/api/movements",
                null);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());

        verifyNoInteractions(chain);
    }

    // CP-200

    @Test
    @DisplayName("Permitir consulta sin encabezado de cuenta")
    void allowReadWithoutAccount() throws Exception {

        var request = request(
                "GET",
                "/api/products",
                null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // CP-201

    @Test
    @DisplayName("Rechazar encabezado con codificacion invalida")
    void rejectMalformedAccount() throws Exception {

        var request = request(
                "POST",
                "/api/movements",
                "%ZZ");

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());

        verifyNoInteractions(chain);
    }

    // CP-202

    @Test
    @DisplayName("No interferir con recursos fuera de la API")
    void allowNonApiRequest() throws Exception {

        var request = request(
                "GET",
                "/css/styles.css",
                null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
