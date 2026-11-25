
package com.stockflow.service;

import com.stockflow.dto.ProductRequest;
import com.stockflow.dto.ProductResponse;
import com.stockflow.model.Category;
import com.stockflow.model.Product;
import com.stockflow.repository.CategoryRepository;
import com.stockflow.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository) {

        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    // Consultar productos activos
    public List<ProductResponse> findAll() {
        return productRepository.findByActiveTrue()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return ProductResponse.from(findActiveProduct(id));
    }

    public List<ProductResponse> findLowStock() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    public List<ProductResponse> findByCategory(Long categoryId) {
        return productRepository
                .findByCategoryIdAndActiveTrue(categoryId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    // Registrar producto
    @Transactional
    public ProductResponse create(ProductRequest request) {

        String sku = normalizeSku(request.sku());

        validateSku(sku, null);

        Product product = new Product();

        applyProductData(product, request);

        product.setStock(0);
        product.setActive(true);

        return ProductResponse.from(
                productRepository.saveAndFlush(product)
        );
    }

    // Actualizar producto
    @Transactional
    public ProductResponse update(
            Long id, ProductRequest request) {

        Product product = findActiveProduct(id);

        if (!Objects.equals(
                product.getVersion(), request.version())) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El producto fue modificado. Recarga la informacion."
            );
        }

        String sku = normalizeSku(request.sku());

        validateSku(sku, id);

        applyProductData(product, request);

        return ProductResponse.from(
                productRepository.saveAndFlush(product)
        );
    }

    // Desactivar producto
    @Transactional
    public void deactivate(Long id) {

        Product product = findActiveProduct(id);

        if (product.getStock() > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No se puede desactivar un producto con existencias"
            );
        }

        product.setActive(false);

        productRepository.saveAndFlush(product);
    }

    // Buscar producto activo
    private Product findActiveProduct(Long id) {

        return productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no encontrado"
                ));
    }

    // Buscar categoria
    private Category findCategory(Long id) {

        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La categoria no existe"
                ));
    }

    // Validar SKU duplicado
    private void validateSku(String sku, Long currentId) {

        productRepository.findBySkuIgnoreCase(sku)
                .ifPresent(existing -> {

                    if (!Objects.equals(
                            existing.getId(), currentId)) {

                        throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "El SKU ya esta registrado"
                        );
                    }
                });
    }

    // Normalizar SKU
    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    // Actualizar informacion general
    private void applyProductData(
            Product product, ProductRequest request) {

        product.setSku(normalizeSku(request.sku()));
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setMinStock(request.minStock());
        product.setCategory(findCategory(request.categoryId()));
    }
}
