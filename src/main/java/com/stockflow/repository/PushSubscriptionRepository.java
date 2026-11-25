package com.stockflow.repository;

import com.stockflow.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository
        extends JpaRepository<PushSubscription, String> {
}