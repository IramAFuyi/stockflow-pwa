
package com.stockflow.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {

        return Map.of(
                "headerName", token.getHeaderName(),
                "token", token.getToken());
    }
}
