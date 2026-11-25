
package com.stockflow.service;

import com.stockflow.dto.MovementRequest;
import com.stockflow.dto.MovementResponse;
import com.stockflow.model.*;
import com.stockflow.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class StockMovementService {

    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final DepartmentRepository departmentRepository;

    public StockMovementService(
            StockMovementRepository movementRepository,
            ProductRepository productRepository,
            DepartmentRepository departmentRepository) {

        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<MovementResponse> findAll() {

        return movementRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(MovementResponse::from)
                .toList();
    }

    @Transactional
    public MovementResponse create(MovementRequest request) {

        // Bloquear el producto durante la transaccion
        Product product = productRepository
                .findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no encontrado"
                ));

        // Comprobar si la operacion ya fue registrada
        var existing = movementRepository
                .findByOperationUuid(request.operationUuid());

        if (existing.isPresent()) {

            StockMovement movement = existing.get();

            validateRepeatedOperation(movement, request);

            return MovementResponse.from(movement);
        }

        if (!product.isActive()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El producto esta desactivado"
            );
        }

        if (request.quantity() == null ||
                request.quantity() <= 0) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La cantidad debe ser mayor que cero"
            );
        }

        Department department = null;

        if (request.departmentId() != null) {

            department = departmentRepository
                .findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El departamento no existe"
                ));
        }

        if (request.type() == MovementType.OUT &&
                department == null) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Indica el departamento receptor"
            );
        }

        if ((request.latitude() == null) !=
                (request.longitude() == null)) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Las coordenadas deben enviarse juntas"
            );
        }

        int previousStock = product.getStock();

        long calculatedStock;

        if (request.type() == MovementType.IN) {

            calculatedStock =
                (long) previousStock + request.quantity();

        } else {

            calculatedStock =
                (long) previousStock - request.quantity();
        }

        if (calculatedStock < 0) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Existencias insuficientes"
            );
        }

        if (calculatedStock > Integer.MAX_VALUE) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Se excede el limite de existencias"
            );
        }

        int resultingStock = (int) calculatedStock;

        product.setStock(resultingStock);

        productRepository.save(product);

        StockMovement movement = new StockMovement(
            request.operationUuid(),
            product,
            department,
            request.type(),
            request.quantity(),
            previousStock,
            resultingStock,
            request.observations(),
            request.latitude(),
            request.longitude()
        );

        return MovementResponse.from(
            movementRepository.saveAndFlush(movement)
        );
    }

    private void validateRepeatedOperation(
            StockMovement movement,
            MovementRequest request) {

        Long departmentId = movement.getDepartment() != null
            ? movement.getDepartment().getId()
            : null;

        boolean sameOperation =
            Objects.equals(
                movement.getProduct().getId(),
                request.productId()
            )
            && Objects.equals(
                departmentId,
                request.departmentId()
            )
            && movement.getType() == request.type()
            && movement.getQuantity() == request.quantity()
            && Objects.equals(
                movement.getObservations(),
                request.observations()
            )
            && Objects.equals(
                movement.getLatitude(),
                request.latitude()
            )
            && Objects.equals(
                movement.getLongitude(),
                request.longitude()
            );

        if (!sameOperation) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El identificador pertenece a otra operacion"
            );
        }
    }
}
