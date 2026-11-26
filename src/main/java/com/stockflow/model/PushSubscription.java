
package com.stockflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @Column(length = 2048)
    private String endpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    @Column(nullable = false)
    private Instant createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(
            String endpoint,
            String p256dh,
            String auth) {

        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = Instant.now();
    }

    public String getEndpoint() {
        return endpoint;
    }

    public AppUser getUser() {
        return user;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
