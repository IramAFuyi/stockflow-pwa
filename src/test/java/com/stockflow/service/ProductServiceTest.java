
package com.stockflow.service;

import com.stockflow.dto.ProductRequest;
import com.stockflow.model.Category;
import com.stockflow.model.Product;
import com.stockflow.repository.CategoryRepository;
import com.stockflow.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private Category category;

    @BeforeEach
    void setUp() {

        category = new Category(
                "Equipos informaticos",
                "Categoria de prueba");

        ReflectionTestUtils.setField(
                category, "id", 1L);

        product = new Product();

        ReflectionTestUtils.setField(
                product, "id", 1L);

        ReflectionTestUtils.setField(
                product, "version", 0L);

        product.setSku("TEST-001");
        product.setName("Laptop de prueba");
        product.setDescription("Producto simulado");
        product.setCategory(category);
        product.setStock(10);
        product.setMinStock(5);
        product.setActive(true);
    }

    // CP-166

    @Test
    @DisplayName("Consultar productos activos")
    void findAllReturnsProducts() {

        when(productRepository.findByActiveTrue())
                .thenReturn(List.of(product));

        var result = productService.findAll();

        assertEquals(1, result.size());

        assertEquals(
                "TEST-001",
                result.getFirst().sku());

        assertEquals(
                10,
                result.getFirst().stock());

        verify(productRepository)
                .findByActiveTrue();
    }

    // CP-167

    @Test
    @DisplayName("Crear producto con stock inicial cero")
    void createProductSuccessfully() {

        ProductRequest request = new ProductRequest(
                " test-002 ",
                "Nuevo producto",
                "Descripcion de prueba",
                5,
                1L,
                null);

        when(productRepository
                .findBySkuIgnoreCase("TEST-002"))
                .thenReturn(Optional.empty());

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(productRepository.saveAndFlush(
                any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = productService.create(request);

        assertEquals("TEST-002", result.sku());
        assertEquals("Nuevo producto", result.name());
        assertEquals(0, result.stock());
        assertEquals(5, result.minStock());
        assertTrue(result.active());

        verify(productRepository)
                .saveAndFlush(any(Product.class));
    }

    // CP-168

    @Test
    @DisplayName("Rechazar SKU duplicado")
    void rejectDuplicateSku() {

        ProductRequest request = new ProductRequest(
                "TEST-001",
                "Producto duplicado",
                null,
                5,
                1L,
                null);

        when(productRepository
                .findBySkuIgnoreCase("TEST-001"))
                .thenReturn(Optional.of(product));

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.create(request));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        verify(productRepository, never())
                .saveAndFlush(any(Product.class));
    }

    // CP-169

    @Test
    @DisplayName("Rechazar categoria inexistente")
    void rejectInvalidCategory() {

        ProductRequest request = new ProductRequest(
                "TEST-003",
                "Producto",
                null,
                5,
                999L,
                null);

        when(productRepository
                .findBySkuIgnoreCase("TEST-003"))
                .thenReturn(Optional.empty());

        when(categoryRepository.findById(999L))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.create(request));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode());

        verify(productRepository, never())
                .saveAndFlush(any(Product.class));
    }

    // CP-170

    @Test
    @DisplayName("Actualizar producto sin alterar existencias")
    void updateProductSuccessfully() {

        ProductRequest request = new ProductRequest(
                "TEST-001",
                "Laptop actualizada",
                "Nueva descripcion",
                3,
                1L,
                0L);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productRepository
                .findBySkuIgnoreCase("TEST-001"))
                .thenReturn(Optional.of(product));

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(productRepository.saveAndFlush(product))
                .thenReturn(product);

        var result = productService.update(
                1L, request);

        assertEquals(
                "Laptop actualizada",
                result.name());

        assertEquals(3, result.minStock());
        assertEquals(10, result.stock());

        verify(productRepository)
                .saveAndFlush(product);
    }

    // CP-171

    @Test
    @DisplayName("Rechazar actualizacion con version obsoleta")
    void rejectOutdatedVersion() {

        ProductRequest request = new ProductRequest(
                "TEST-001",
                "Actualizacion desfasada",
                null,
                5,
                1L,
                99L);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.update(1L, request));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        verify(productRepository, never())
                .saveAndFlush(any(Product.class));
    }

    // CP-172

    @Test
    @DisplayName("Desactivar producto sin existencias")
    void deactivateEmptyProduct() {

        product.setStock(0);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        productService.deactivate(1L);

        assertFalse(product.isActive());

        verify(productRepository)
                .saveAndFlush(product);
    }

    // CP-173

    @Test
    @DisplayName("Impedir desactivar producto con existencias")
    void rejectDeactivationWithStock() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.deactivate(1L));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        assertTrue(product.isActive());

        verify(productRepository, never())
                .saveAndFlush(any(Product.class));
    }
}
