
package com.stockflow.config;

import com.stockflow.model.AppUser;
import com.stockflow.model.UserRole;
import com.stockflow.repository.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Component
public class UserInitializer implements CommandLineRunner {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${stockflow.admin.username}")
    private String username;

    @Value("${stockflow.admin.password}")
    private String password;

    @Value("${stockflow.admin.full-name}")
    private String fullName;

    public UserInitializer(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);

        if (repository.existsByUsernameIgnoreCase(
                normalizedUsername)) {

            return;
        }

        if (password.length() < 12 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new IllegalStateException(
                    "Configura una contrasena ADMIN_PASSWORD valida");
        }

        AppUser administrator = new AppUser(
                normalizedUsername,
                passwordEncoder.encode(password),
                fullName,
                UserRole.ADMIN);

        repository.save(administrator);

        System.out.println(
                "[StockFlow] Usuario administrador creado");
    }
}
