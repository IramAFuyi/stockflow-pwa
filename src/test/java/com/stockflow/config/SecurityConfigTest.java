
package com.stockflow.config;

import com.stockflow.controller.ProductController;
import com.stockflow.controller.StockMovementController;
import com.stockflow.controller.CsrfController;

import com.stockflow.service.ProductService;
import com.stockflow.service.StockMovementService;
import com.stockflow.dto.MovementRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import org.springframework.http.MediaType;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;

@SpringJUnitWebConfig(SecurityConfigTest.TestConfig.class)
class SecurityConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({
            SecurityConfig.class,
            ProductController.class,
            StockMovementController.class,
            CsrfController.class
    })
    static class TestConfig {

        @Bean
        UserDetailsService userDetailsService(
                PasswordEncoder encoder) {

            var admin = User.withUsername("admin")
                    .password(encoder.encode("AdminTest12345"))
                    .roles("ADMIN")
                    .build();

            var operator = User.withUsername("operador")
                    .password(encoder.encode("OperatorTest12345"))
                    .roles("OPERATOR")
                    .build();

            return new InMemoryUserDetailsManager(
                    admin, operator);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private StockMovementService movementService;

    private MockMvc mvc;

    private static final String PRODUCT_JSON = """
            {
                "sku": "TEST-SECURITY",
                "name": "Producto de prueba",
                "description": "Prueba automatizada",
                "categoryId": 1,
                "minStock": 5,
                "version": 0
            }
            """;

    private static final String MOVEMENT_JSON = """
            {
                "operationUuid":
                    "550e8400-e29b-41d4-a716-446655440000",
                "productId": 1,
                "departmentId": null,
                "type": "IN",
                "quantity": 1,
                "observations": "Prueba automatizada",
                "latitude": null,
                "longitude": null
            }
            """;

    @BeforeEach
    void setUp() {

        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // CP-203

    @Test
    @DisplayName("Rechazar consulta sin autenticacion")
    void rejectAnonymousRequest() throws Exception {

        mvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService);
    }

    // CP-204

    @Test
    @DisplayName("Permitir consulta de inventario al operador")
    void allowOperatorRead() throws Exception {

        mvc.perform(get("/api/products")
                .with(user("operador").roles("OPERATOR")))

                .andExpect(status().isOk());

        verify(productService).findAll();
    }

    // CP-205

    @Test
    @DisplayName("Prohibir modificaciones de catalogo al operador")
    void rejectOperatorWrites() throws Exception {

        mvc.perform(post("/api/products")
                .with(user("operador").roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PRODUCT_JSON))

                .andExpect(status().isForbidden());

        mvc.perform(put("/api/products/1")
                .with(user("operador").roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PRODUCT_JSON))

                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/products/1")
                .with(user("operador").roles("OPERATOR"))
                .with(csrf()))

                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    // CP-206

    @Test
    @DisplayName("Permitir administracion de catalogo al administrador")
    void allowAdministratorWrites() throws Exception {

        mvc.perform(post("/api/products")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PRODUCT_JSON))

                .andExpect(status().isCreated());

        mvc.perform(put("/api/products/1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PRODUCT_JSON))

                .andExpect(status().isOk());

        mvc.perform(delete("/api/products/1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))

                .andExpect(status().isNoContent());

        verify(productService).create(any());
        verify(productService).update(eq(1L), any());
        verify(productService).deactivate(1L);
    }

    // CP-207

    @Test
    @DisplayName("Rechazar modificacion sin token CSRF")
    void rejectMissingCsrfToken() throws Exception {

        mvc.perform(post("/api/products")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(PRODUCT_JSON))

                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    // CP-208

    @Test
    @DisplayName("Permitir movimiento autenticado del operador")
    void allowOperatorMovement() throws Exception {

        mvc.perform(post("/api/movements")
                .servletPath("/api/movements")
                .with(user("operador").roles("OPERATOR"))
                .with(csrf())
                .header("X-StockFlow-Account", "operador")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOVEMENT_JSON))

                .andExpect(status().isOk());

        verify(movementService).create(
                any(MovementRequest.class),
                eq("operador"));
    }

    // CP-209

    @Test
    @DisplayName("Rechazar movimiento con identidad incorrecta")
    void rejectMovementWithWrongAccount() throws Exception {

        mvc.perform(post("/api/movements")
                .servletPath("/api/movements")
                .with(user("operador").roles("OPERATOR"))
                .with(csrf())
                .header("X-StockFlow-Account", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOVEMENT_JSON))

                .andExpect(status().isForbidden());

        verifyNoInteractions(movementService);
    }

    // CP-210

    @Test
    @DisplayName("Autenticar credenciales validas")
    void successfulLogin() throws Exception {

        mvc.perform(formLogin()
                .user("operador")
                .password("OperatorTest12345"))

                .andExpect(authenticated()
                        .withUsername("operador"))

                .andExpect(redirectedUrl("/"));
    }

    // CP-211

    @Test
    @DisplayName("Rechazar contrasena incorrecta")
    void rejectInvalidPassword() throws Exception {

        mvc.perform(formLogin()
                .user("operador")
                .password("incorrecta"))

                .andExpect(unauthenticated())

                .andExpect(redirectedUrl("/login?error"));
    }
}
