package com.stockflow.repository;

import com.stockflow.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long> {

    Optional<StockMovement> findByOperationUuid(UUID operationUuid);

    List<StockMovement> findAllByOrderByCreatedAtDesc();

    List<StockMovement>
        findByProductIdOrderByCreatedAtDesc(Long productId);
}