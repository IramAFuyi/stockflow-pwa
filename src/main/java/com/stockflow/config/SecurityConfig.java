
package com.stockflow.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        
http
        .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                                "default-src 'self'; " +
                                "script-src 'self'; " +
                                "style-src 'self'; " +
                                "img-src 'self'; " +
                                "font-src 'self'; " +
                                "connect-src 'self'; " +
                                "worker-src 'self'; " +
                                "manifest-src 'self'; " +
                                "object-src 'none'; " +
                                "frame-src 'none'; " +
                                "frame-ancestors 'none'; " +
                                "base-uri 'self'; " +
                                "form-action 'self';"
                        )
                )
        )

        .authorizeHttpRequests(auth -> auth


                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/icons/**",
                                "/manifest.json",
                                "/sw.js",
                                "/offline.html",
                                "/app-shell.html")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products",
                                "/api/products/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/products/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/products/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products",
                                "/api/products/**",
                                "/api/categories",
                                "/api/departments",
                                "/api/movements",
                                "/api/push/public-key",
                                "/api/csrf")
                        .hasAnyRole("ADMIN", "OPERATOR")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/movements",
                                "/api/push/subscribe",
                                "/api/push/status")
                        .hasAnyRole("ADMIN", "OPERATOR")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/push/subscribe")
                        .hasAnyRole("ADMIN", "OPERATOR")

                        .requestMatchers(
                                "/",
                                "/movements")
                        .hasAnyRole("ADMIN", "OPERATOR")

                        .anyRequest().denyAll())

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())

                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI()
                                        .startsWith(
                                                request.getContextPath() + "/api/")));
        http.addFilterAfter(
                new AccountBindingFilter(),
                AuthorizationFilter.class);
        return http.build();
    }
}
