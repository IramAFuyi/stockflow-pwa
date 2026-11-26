
package com.stockflow.service;

import com.stockflow.model.AppUser;
import com.stockflow.model.UserRole;
import com.stockflow.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private AppUserRepository repository;

    @InjectMocks
    private CustomUserDetailsService service;

    // CP-184

    @Test
    @DisplayName("Cargar usuario administrador")
    void loadAdministrator() {

        AppUser user = new AppUser(
                "admin",
                "hash-simulado",
                "Administrador",
                UserRole.ADMIN);

        when(repository.findByUsernameIgnoreCase("admin"))
                .thenReturn(Optional.of(user));

        var result = service.loadUserByUsername("admin");

        assertEquals("admin", result.getUsername());
        assertEquals("hash-simulado", result.getPassword());
        assertTrue(result.isEnabled());

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    // CP-185

    @Test
    @DisplayName("Cargar usuario operador")
    void loadOperator() {

        AppUser user = new AppUser(
                "operador",
                "hash-simulado",
                "Operador",
                UserRole.OPERATOR);

        when(repository.findByUsernameIgnoreCase("operador"))
                .thenReturn(Optional.of(user));

        var result = service.loadUserByUsername("operador");

        assertEquals("operador", result.getUsername());

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR")));

        assertFalse(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    // CP-186

    @Test
    @DisplayName("Rechazar usuario inexistente")
    void rejectUnknownUser() {

        when(repository.findByUsernameIgnoreCase("inexistente"))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("inexistente"));
    }

    // CP-187

    @Test
    @DisplayName("Identificar usuario desactivado")
    void loadDisabledUser() {

        AppUser user = new AppUser(
                "operador",
                "hash-simulado",
                "Operador desactivado",
                UserRole.OPERATOR);

        ReflectionTestUtils.setField(
                user, "active", false);

        when(repository.findByUsernameIgnoreCase("operador"))
                .thenReturn(Optional.of(user));

        var result = service.loadUserByUsername("operador");

        assertFalse(result.isEnabled());
    }
}
