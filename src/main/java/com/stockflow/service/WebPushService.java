
package com.stockflow.service;

import com.stockflow.model.PushSubscription;
import com.stockflow.dto.PushSubscriptionRequest;
import com.stockflow.repository.PushSubscriptionRepository;

import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Encoding;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.apache.http.util.EntityUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Base64;

@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com");

    private final PushSubscriptionRepository repository;

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebPushService(
            PushSubscriptionRepository repository,

            @Value("${stockflow.push.public-key}") String publicKey,

            @Value("${stockflow.push.private-key}") String privateKey,

            @Value("${stockflow.push.subject}") String subject) {

        this.repository = repository;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;

        if (Security.getProvider("BC") == null) {
            Security.addProvider(
                    new BouncyCastleProvider());
        }
    }

    public boolean isConfigured() {

        return !publicKey.isBlank()
                && !privateKey.isBlank()
                && !subject.isBlank();
    }

    public String getPublicKey() {

        if (!isConfigured()) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Web Push no esta configurado");
        }

        return publicKey;
    }

    private boolean validEndpoint(String endpoint) {

        try {

            URI uri = URI.create(endpoint);

            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && ALLOWED_HOSTS.contains(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null
                    && (uri.getPort() == -1
                            || uri.getPort() == 443);

        } catch (Exception ex) {
            return false;
        }
    }

    private boolean validKeys(
            PushSubscriptionRequest request) {

        try {

            byte[] key = Base64.getUrlDecoder()
                    .decode(request.p256dh());

            byte[] auth = Base64.getUrlDecoder()
                    .decode(request.auth());

            return key.length == 65
                    && key[0] == 4
                    && auth.length == 16;

        } catch (Exception ex) {
            return false;
        }
    }

    public void subscribe(
            PushSubscriptionRequest request) {

        getPublicKey();

        if (!validEndpoint(request.endpoint())
                || !validKeys(request)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Suscripcion push invalida");
        }

        repository.save(
                new PushSubscription(
                        request.endpoint(),
                        request.p256dh(),
                        request.auth()));
    }

    public void unsubscribe(String endpoint) {
        repository.deleteById(endpoint);
    }

    public void sendToAll(String message) {

        if (!isConfigured()) {
            return;
        }

        for (PushSubscription subscription : repository.findAll()) {

            send(subscription, message);
        }
    }

    private void send(
            PushSubscription subscription,
            String message) {

        if (!validEndpoint(subscription.getEndpoint())) {
            return;
        }

        try {

            PushService pushService = new PushService(
                    publicKey,
                    privateKey,
                    subject);

            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    message.getBytes(StandardCharsets.UTF_8),
                    3600);

            var post = pushService.preparePost(
                    notification,
                    Encoding.AES128GCM);

            byte[] body = EntityUtils.toByteArray(
                    post.getEntity());

            var builder = HttpRequest.newBuilder(
                    post.getURI())
                    .timeout(Duration.ofSeconds(15))
                    .POST(
                            HttpRequest.BodyPublishers.ofByteArray(body));

            for (var header : post.getAllHeaders()) {

                if (!header.getName().equalsIgnoreCase("Host")
                        && !header.getName().equalsIgnoreCase(
                                "Content-Length")) {

                    builder.header(
                            header.getName(),
                            header.getValue());
                }
            }

            var response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.discarding());

            int status = response.statusCode();

            if (status == 404 || status == 410) {

                repository.deleteById(
                        subscription.getEndpoint());

                log.info(
                        "Suscripcion push expirada eliminada");

            } else if (status >= 200 && status < 300) {

                log.info(
                        "Notificacion aceptada por proveedor push: {}",
                        status);

            } else {

                log.warn(
                        "Proveedor push devolvio HTTP {}",
                        status);
            }

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            log.warn("Envio push interrumpido");

        } catch (Exception ex) {

            log.warn(
                    "No se pudo enviar Web Push: {}",
                    ex.getClass().getSimpleName());
        }
    }
}
