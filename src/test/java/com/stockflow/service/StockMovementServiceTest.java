
package com.stockflow.service;

import com.stockflow.dto.MovementRequest;
import com.stockflow.model.*;
import com.stockflow.repository.*;
import com.stockflow.event.LowStockEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository movementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StockMovementService movementService;

    private Product product;
    private Department department;
    private AppUser user;

    @BeforeEach
    void setUp() {

        Category category = new Category(
                "Equipos",
                "Categoria de prueba");

        ReflectionTestUtils.setField(
                category, "id", 1L);

        product = new Product();

        ReflectionTestUtils.setField(
                product, "id", 1L);

        product.setSku("TEST-001");
        product.setName("Laptop");
        product.setCategory(category);
        product.setStock(10);
        product.setMinStock(5);
        product.setActive(true);

        department = new Department(
                "Sistemas",
                "Departamento de prueba");

        ReflectionTestUtils.setField(
                department, "id", 1L);

        user = new AppUser(
                "operador",
                "hash-simulado",
                "Usuario de prueba",
                UserRole.OPERATOR);

        ReflectionTestUtils.setField(
                user, "id", 2L);
    }

    private MovementRequest request(
            MovementType type,
            int quantity,
            Long departmentId) {

        return new MovementRequest(
                UUID.randomUUID(),
                1L,
                departmentId,
                type,
                quantity,
                "Movimiento de prueba",
                null,
                null);
    }

    private void mockIdentityAndProduct() {

        when(userRepository
                .findByUsernameIgnoreCase("operador"))
                .thenReturn(Optional.of(user));

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(product));
    }

    private void mockNewMovement(
            MovementRequest request) {

        mockIdentityAndProduct();

        when(movementRepository
                .findByOperationUuid(
                        request.operationUuid()))
                .thenReturn(Optional.empty());
    }

    private void mockMovementSave() {

        when(movementRepository.saveAndFlush(
                any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // CP-174

    @Test
    @DisplayName("Registrar entrada y aumentar existencias")
    void registerEntry() {

        var request = request(
                MovementType.IN, 5, null);

        mockNewMovement(request);
        mockMovementSave();

        var result = movementService.create(
                request, "operador");

        assertEquals(10, result.previousStock());
        assertEquals(15, result.resultingStock());
        assertEquals(15, product.getStock());

        assertEquals(
                "operador",
                result.registeredBy());

        assertEquals(
                request.operationUuid(),
                result.operationUuid());

        verify(productRepository).save(product);

        verify(movementRepository)
                .saveAndFlush(any(StockMovement.class));

        verifyNoInteractions(eventPublisher);
    }

    // CP-175

    @Test
    @DisplayName("Registrar salida y generar alerta de stock minimo")
    void registerExitAndPublishEvent() {

        var request = request(
                MovementType.OUT, 5, 1L);

        mockNewMovement(request);
        mockMovementSave();

        when(departmentRepository.findById(1L))
                .thenReturn(Optional.of(department));

        var result = movementService.create(
                request, "operador");

        assertEquals(10, result.previousStock());
        assertEquals(5, result.resultingStock());
        assertEquals(5, product.getStock());

        assertEquals(
                "Sistemas",
                result.departmentName());

        verify(eventPublisher)
                .publishEvent(any(LowStockEvent.class));
    }

    // CP-176

    @Test
    @DisplayName("Rechazar salida con existencias insuficientes")
    void rejectInsufficientStock() {

        var request = request(
                MovementType.OUT, 11, 1L);

        mockNewMovement(request);

        when(departmentRepository.findById(1L))
                .thenReturn(Optional.of(department));

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> movementService.create(
                        request, "operador"));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        assertEquals(10, product.getStock());

        verify(productRepository, never())
                .save(any(Product.class));

        verify(movementRepository, never())
                .saveAndFlush(any(StockMovement.class));

        verifyNoInteractions(eventPublisher);
    }

    // CP-177

    @Test
    @DisplayName("Rechazar salida sin departamento")
    void rejectExitWithoutDepartment() {

        var request = request(
                MovementType.OUT, 2, null);

        mockNewMovement(request);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> movementService.create(
                        request, "operador"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode());

        assertEquals(10, product.getStock());

        verify(movementRepository, never())
                .saveAndFlush(any(StockMovement.class));
    }

    // CP-178

    @Test
    @DisplayName("Rechazar coordenadas incompletas")
    void rejectIncompleteCoordinates() {

        var request = new MovementRequest(
                UUID.randomUUID(),
                1L,
                null,
                MovementType.IN,
                1,
                "Prueba de geolocalizacion",
                28.63,
                null);

        mockNewMovement(request);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> movementService.create(
                        request, "operador"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode());

        verify(movementRepository, never())
                .saveAndFlush(any(StockMovement.class));
    }

    // CP-179

    @Test
    @DisplayName("Mantener idempotencia de un movimiento repetido")
    void repeatedOperationDoesNotModifyStock() {

        var request = request(
                MovementType.IN, 5, null);

        StockMovement existing = new StockMovement(
                request.operationUuid(),
                product,
                null,
                user,
                MovementType.IN,
                5,
                5,
                10,
                request.observations(),
                null,
                null);

        mockIdentityAndProduct();

        when(movementRepository.findByOperationUuid(
                request.operationUuid()))
                .thenReturn(Optional.of(existing));

        var result = movementService.create(
                request, "operador");

        assertEquals(
                request.operationUuid(),
                result.operationUuid());

        assertEquals(10, result.resultingStock());
        assertEquals(10, product.getStock());

        verify(productRepository, never())
                .save(any(Product.class));

        verify(movementRepository, never())
                .saveAndFlush(any(StockMovement.class));

        verifyNoInteractions(eventPublisher);
    }

    // CP-180

    @Test
    @DisplayName("Rechazar UUID perteneciente a otro usuario")
    void rejectOperationOwnedByAnotherUser() {

        AppUser administrator = new AppUser(
                "admin",
                "hash-simulado",
                "Administrador",
                UserRole.ADMIN);

        ReflectionTestUtils.setField(
                administrator, "id", 1L);

        var request = request(
                MovementType.IN, 5, null);

        StockMovement existing = new StockMovement(
                request.operationUuid(),
                product,
                null,
                administrator,
                MovementType.IN,
                5,
                5,
                10,
                request.observations(),
                null,
                null);

        mockIdentityAndProduct();

        when(movementRepository.findByOperationUuid(
                request.operationUuid()))
                .thenReturn(Optional.of(existing));

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> movementService.create(
                        request, "operador"));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        assertEquals(10, product.getStock());

        verify(productRepository, never())
                .save(any(Product.class));

        verify(movementRepository, never())
                .saveAndFlush(any(StockMovement.class));
    }

    // CP-181

    @Test
    @DisplayName("Rechazar movimiento en producto desactivado")
    void rejectInactiveProduct() {

        product.setActive(false);

        var request = request(
                MovementType.IN, 1, null);

        mockNewMovement(request);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> movementService.create(
                        request, "operador"));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        verify(movementRepository, never())
                .saveAndFlush(any(StockMovement.class));
    }

    // CP-182

    @Test
    @DisplayName("Impedir desbordamiento de existencias")
    void rejectStockOverflow() {

        product.setStock(Integer.MAX_VALUE);

        var request = request(
                MovementType.IN, 1, null);

        mockNewMovement(request);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> movementService.create(
                        request, "operador"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode());

        assertEquals(
                Integer.MAX_VALUE,
                product.getStock());

        verify(movementRepository, never())
                .saveAndFlush(any(StockMovement.class));
    }

    // CP-183

    @Test
    @DisplayName("Rechazar usuario no autorizado")
    void rejectUnknownUser() {

        var request = request(
                MovementType.IN, 1, null);

        when(userRepository
                .findByUsernameIgnoreCase("desconocido"))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> movementService.create(
                        request, "desconocido"));

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(movementRepository);
        verifyNoInteractions(eventPublisher);
    }
}
