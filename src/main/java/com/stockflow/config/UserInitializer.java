
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
    private String adminUsername;

    @Value("${stockflow.admin.password}")
    private String adminPassword;

    @Value("${stockflow.admin.full-name}")
    private String adminFullName;

    @Value("${stockflow.operator.username}")
    private String operatorUsername;

    @Value("${stockflow.operator.password}")
    private String operatorPassword;

    @Value("${stockflow.operator.full-name}")
    private String operatorFullName;

    public UserInitializer(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        createUserIfMissing(
                adminUsername,
                adminPassword,
                adminFullName,
                UserRole.ADMIN);

        if (!operatorPassword.isBlank()) {

            createUserIfMissing(
                    operatorUsername,
                    operatorPassword,
                    operatorFullName,
                    UserRole.OPERATOR);
        }
    }

    private void createUserIfMissing(
            String username,
            String password,
            String fullName,
            UserRole role) {

        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);

        if (normalizedUsername.isBlank() ||
                normalizedUsername.length() > 80) {

            throw new IllegalStateException(
                    "Nombre de usuario inicial invalido");
        }

        var existing = repository
                .findByUsernameIgnoreCase(normalizedUsername);

        if (existing.isPresent()) {

            if (existing.get().getRole() != role) {

                throw new IllegalStateException(
                        "El usuario inicial tiene un rol diferente");
            }

            return;
        }

        if (password.length() < 12 ||
                password.getBytes(
                        StandardCharsets.UTF_8).length > 72) {

            throw new IllegalStateException(
                    "Configura una contrasena valida para " + role);
        }

        AppUser user = new AppUser(
                normalizedUsername,
                passwordEncoder.encode(password),
                fullName,
                role);

        repository.save(user);

        System.out.println(
                "[StockFlow] Usuario " + role + " creado");
    }
}
