
package com.stockflow.controller;

import com.stockflow.dto.PushSubscriptionRequest;
import com.stockflow.service.WebPushService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final WebPushService pushService;

    public PushController(WebPushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/public-key")
    public Map<String, String> publicKey() {

        return Map.of(
                "publicKey", pushService.getPublicKey());
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(
            @Valid @RequestBody PushSubscriptionRequest request) {

        pushService.subscribe(request);
    }

    @DeleteMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @RequestBody Map<String, String> request) {

        String endpoint = request.get("endpoint");

        if (endpoint == null || endpoint.isBlank()) {

            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Endpoint obligatorio");
        }

        pushService.unsubscribe(endpoint);
    }
}
