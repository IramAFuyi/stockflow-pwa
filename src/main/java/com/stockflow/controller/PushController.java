
package com.stockflow.controller;

import com.stockflow.dto.PushSubscriptionRequest;
import com.stockflow.service.WebPushService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
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
            @Valid @RequestBody PushSubscriptionRequest request,
            Principal principal) {

        pushService.subscribe(
                request,
                principal.getName());
    }

    @DeleteMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @RequestBody Map<String, String> request,
            Principal principal) {

        pushService.unsubscribe(
                requireEndpoint(request),
                principal.getName());
    }

    @PostMapping("/status")
    public Map<String, Boolean> status(
            @RequestBody Map<String, String> request,
            Principal principal) {

        return Map.of(
                "subscribed",
                pushService.isSubscribed(
                        requireEndpoint(request),
                        principal.getName()));
    }

    private String requireEndpoint(
            Map<String, String> request) {

        String endpoint = request.get("endpoint");

        if (endpoint == null ||
                endpoint.isBlank() ||
                endpoint.length() > 2048) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Endpoint invalido");
        }

        return endpoint;
    }
}
