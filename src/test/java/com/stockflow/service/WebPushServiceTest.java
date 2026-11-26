
package com.stockflow.service;

import com.stockflow.dto.PushSubscriptionRequest;
import com.stockflow.model.AppUser;
import com.stockflow.model.UserRole;
import com.stockflow.repository.AppUserRepository;
import com.stockflow.repository.PushSubscriptionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebPushServiceTest {

    @Mock
    private PushSubscriptionRepository repository;

    @Mock
    private AppUserRepository userRepository;

    private WebPushService service;

    private AppUser user;

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/test";

    private String p256dh;
    private String auth;

    @BeforeEach
    void setUp() {

        service = new WebPushService(
                repository,
                userRepository,
                "public-key-test",
                "private-key-test",
                "mailto:test@example.com");

        user = new AppUser(
                "operador",
                "hash-simulado",
                "Operador",
                UserRole.OPERATOR);

        ReflectionTestUtils.setField(
                user, "id", 2L);

        byte[] publicKey = new byte[65];

        publicKey[0] = 4;

        p256dh = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(publicKey);

        auth = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(new byte[16]);
    }

    private void mockActiveUser() {

        when(userRepository
                .findByUsernameIgnoreCase("operador"))
                .thenReturn(Optional.of(user));
    }

    private PushSubscriptionRequest request() {

        return new PushSubscriptionRequest(
                ENDPOINT, p256dh, auth);
    }

    // CP-188

    @Test
    @DisplayName("Rechazar Web Push sin configuracion VAPID")
    void rejectMissingConfiguration() {

        WebPushService unconfigured = new WebPushService(
                repository,
                userRepository,
                "",
                "",
                "");

        assertFalse(unconfigured.isConfigured());

        var exception = assertThrows(
                ResponseStatusException.class,
                unconfigured::getPublicKey);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode());

        verifyNoInteractions(repository);
    }

    // CP-189

    @Test
    @DisplayName("Registrar suscripcion del usuario autenticado")
    void registerSubscription() {

        mockActiveUser();

        when(repository.registerSubscription(
                ENDPOINT, p256dh, auth, 2L))
                .thenReturn(1);

        assertDoesNotThrow(() -> service.subscribe(request(), "operador"));

        verify(repository).registerSubscription(
                ENDPOINT, p256dh, auth, 2L);
    }

    // CP-190

    @Test
    @DisplayName("Rechazar endpoint no autorizado")
    void rejectInvalidEndpoint() {

        mockActiveUser();

        var request = new PushSubscriptionRequest(
                "https://example.com/push",
                p256dh,
                auth);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.subscribe(request, "operador"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode());

        verifyNoInteractions(repository);
    }

    // CP-191

    @Test
    @DisplayName("Rechazar claves de suscripcion invalidas")
    void rejectInvalidKeys() {

        mockActiveUser();

        var request = new PushSubscriptionRequest(
                ENDPOINT,
                "clave-invalida",
                "auth-invalido");

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.subscribe(request, "operador"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode());

        verifyNoInteractions(repository);
    }

    // CP-192

    @Test
    @DisplayName("Rechazar conflicto de propiedad")
    void rejectSubscriptionOwnedByAnotherUser() {

        mockActiveUser();

        when(repository.registerSubscription(
                ENDPOINT, p256dh, auth, 2L))
                .thenReturn(0);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.subscribe(request(), "operador"));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());
    }

    // CP-193

    @Test
    @DisplayName("Eliminar suscripcion mediante su propietario")
    void deleteOwnedSubscription() {

        mockActiveUser();

        service.unsubscribe(ENDPOINT, "operador");

        verify(repository).deleteOwnedSubscription(
                ENDPOINT, 2L);

        verify(repository, never())
                .deleteById(anyString());
    }

    // CP-194

    @Test
    @DisplayName("Consultar suscripcion de la cuenta actual")
    void checkSubscriptionOwnership() {

        mockActiveUser();

        when(repository.existsByEndpointAndUser_Id(
                ENDPOINT, 2L))
                .thenReturn(true, false);

        assertTrue(
                service.isSubscribed(ENDPOINT, "operador"));

        assertFalse(
                service.isSubscribed(ENDPOINT, "operador"));

        verify(repository, times(2))
                .existsByEndpointAndUser_Id(ENDPOINT, 2L);
    }

    // CP-195

    @Test
    @DisplayName("Consultar exclusivamente suscripciones activas")
    void selectActiveSubscriptions() {

        when(repository.findAllByUser_ActiveTrue())
                .thenReturn(List.of());

        service.sendToAll("Notificacion de prueba");

        verify(repository).findAllByUser_ActiveTrue();

        verify(repository, never()).findAll();
    }

    // CP-196

    @Test
    @DisplayName("Rechazar suscripcion de usuario inexistente")
    void rejectUnknownUser() {

        when(userRepository
                .findByUsernameIgnoreCase("desconocido"))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.subscribe(
                        request(), "desconocido"));

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode());

        verifyNoInteractions(repository);
    }
}
