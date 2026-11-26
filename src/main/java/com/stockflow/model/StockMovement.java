
package com.stockflow.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Check(constraints = """
        quantity > 0 AND
        previous_stock >= 0 AND
        resulting_stock >= 0
        """)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_uuid", nullable = false, unique = true, updatable = false)
    private UUID operationUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MovementType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "previous_stock", nullable = false)
    private int previousStock;

    @Column(name = "resulting_stock", nullable = false)
    private int resultingStock;

    @Column(length = 500)
    private String observations;

    private Double latitude;

    private Double longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(
            UUID operationUuid,
            Product product,
            Department department,
            AppUser user,
            MovementType type,
            int quantity,
            int previousStock,
            int resultingStock,
            String observations,
            Double latitude,
            Double longitude) {

        this.operationUuid = operationUuid;
        this.product = product;
        this.department = department;
        this.user = java.util.Objects.requireNonNull(user);
        this.type = type;
        this.quantity = quantity;
        this.previousStock = previousStock;
        this.resultingStock = resultingStock;
        this.observations = observations;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = Instant.now();
    }

    public AppUser getUser() {
        return user;
    }

    public Long getId() {
        return id;
    }

    public UUID getOperationUuid() {
        return operationUuid;
    }

    public Product getProduct() {
        return product;
    }

    public Department getDepartment() {
        return department;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getPreviousStock() {
        return previousStock;
    }

    public int getResultingStock() {
        return resultingStock;
    }

    public String getObservations() {
        return observations;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
