
package com.stockflow.repository;

import com.stockflow.model.PushSubscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PushSubscriptionRepository
        extends JpaRepository<PushSubscription, String> {

    List<PushSubscription> findAllByUser_ActiveTrue();

    boolean existsByEndpointAndUser_Id(
            String endpoint,
            Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO push_subscriptions
                (endpoint, p256dh, auth, created_at, user_id)
            VALUES
                (:endpoint, :p256dh, :auth, CURRENT_TIMESTAMP, :userId)
            ON CONFLICT (endpoint) DO UPDATE
            SET p256dh = EXCLUDED.p256dh,
                auth = EXCLUDED.auth,
                user_id = EXCLUDED.user_id
            WHERE push_subscriptions.user_id = EXCLUDED.user_id
               OR (
                   push_subscriptions.user_id IS NULL
                   AND push_subscriptions.p256dh = EXCLUDED.p256dh
                   AND push_subscriptions.auth = EXCLUDED.auth
               )
            """, nativeQuery = true)
    int registerSubscription(
            @Param("endpoint") String endpoint,
            @Param("p256dh") String p256dh,
            @Param("auth") String auth,
            @Param("userId") Long userId);

    @Modifying
    @Query(value = """
            DELETE FROM push_subscriptions
            WHERE endpoint = :endpoint
              AND user_id = :userId
            """, nativeQuery = true)
    int deleteOwnedSubscription(
            @Param("endpoint") String endpoint,
            @Param("userId") Long userId);
}
