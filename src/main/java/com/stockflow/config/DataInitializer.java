
package com.stockflow.config;

import com.stockflow.model.Category;
import com.stockflow.model.Product;
import com.stockflow.repository.CategoryRepository;
import com.stockflow.repository.ProductRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.stockflow.model.Department;
import com.stockflow.repository.DepartmentRepository;

@Component
@ConditionalOnProperty(
    name = "stockflow.demo-data",
    havingValue = "true"
)
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final DepartmentRepository departmentRepository;

    public DataInitializer(
        CategoryRepository categoryRepository,
        ProductRepository productRepository,
        DepartmentRepository departmentRepository) {

    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.departmentRepository = departmentRepository;
}

    @Override
    @Transactional
    public void run(String... args) {

        Category computers = createCategory(
            "Equipos de computo", "Computadoras y laptops");

        Category peripherals = createCategory(
            "Perifericos", "Accesorios para computadoras");

        Category networking = createCategory(
            "Redes", "Equipos de infraestructura de red");

        Category storage = createCategory(
            "Almacenamiento", "Dispositivos de almacenamiento");

        Category supplies = createCategory(
            "Consumibles", "Material de impresion");

        Category cables = createCategory(
            "Cableado", "Cables y adaptadores");

        Category components = createCategory(
            "Componentes", "Componentes internos de computadoras");

        createProduct("EQ-001", "Laptop Dell Latitude",
            "Laptop empresarial", 12, 3, computers);

        createProduct("EQ-002", "HP ProDesk",
            "Computadora de escritorio", 8, 3, computers);

        createProduct("EQ-003", "Lenovo ThinkPad",
            "Laptop para uso administrativo", 6, 2, computers);

        createProduct("PER-001", "Monitor Samsung 24 pulgadas",
            "Monitor Full HD", 24, 5, peripherals);

        createProduct("PER-002", "Teclado Logitech K120",
            "Teclado USB", 3, 5, peripherals);

        createProduct("PER-003", "Mouse Logitech M185",
            "Mouse inalambrico", 38, 10, peripherals);

        createProduct("PER-004", "Webcam Logitech C920",
            "Camara web Full HD", 7, 3, peripherals);

        createProduct("RED-001", "Switch TP-Link 24 puertos",
            "Switch Gigabit Ethernet", 4, 2, networking);

        createProduct("RED-002", "Router TP-Link",
            "Router inalambrico", 5, 2, networking);

        createProduct("RED-003", "Access Point Ubiquiti",
            "Punto de acceso empresarial", 2, 3, networking);

        createProduct("ALM-001", "SSD Kingston 480 GB",
            "Unidad de estado solido SATA", 16, 5, storage);

        createProduct("ALM-002", "Disco duro Seagate 2 TB",
            "Disco duro interno", 8, 3, storage);

        createProduct("ALM-003", "Memoria USB 64 GB",
            "Almacenamiento portatil", 25, 5, storage);

        createProduct("CON-001", "Toner HP 85A",
            "Consumible de impresion laser", 2, 5, supplies);

        createProduct("CON-002", "Cartucho Epson negro",
            "Consumible de impresion", 10, 4, supplies);

        createProduct("CAB-001", "Cable Ethernet CAT6",
            "Cable de red de 3 metros", 50, 10, cables);

        createProduct("CAB-002", "Cable HDMI",
            "Cable de video de 2 metros", 18, 5, cables);

        createProduct("CAB-003", "Adaptador USB-C a HDMI",
            "Adaptador de video", 0, 3, cables);

        createProduct("CMP-001", "Memoria RAM DDR4 8 GB",
            "Modulo de memoria RAM", 14, 5, components);

        createProduct("CMP-002", "Fuente de poder 500 W",
            "Fuente de alimentacion ATX", 4, 3, components);
        
        createDepartment("Sistemas", "Soporte e infraestructura TI");
        createDepartment("Administracion", "Gestion administrativa");
        createDepartment("Contabilidad", "Control financiero");
        createDepartment("Direccion", "Direccion general");
        createDepartment("Ventas", "Area comercial");
        createDepartment("Compras", "Adquisicion de materiales");
        createDepartment("Almacen", "Control de mercancia");
        createDepartment("Logistica", "Distribucion y transporte");
    }

    private Category createCategory(
            String name, String description) {

        return categoryRepository.findByNameIgnoreCase(name)
            .orElseGet(() -> categoryRepository.save(
                new Category(name, description)
            ));
    }

    private void createProduct(
            String sku,
            String name,
            String description,
            int stock,
            int minStock,
            Category category) {

        if (productRepository.existsBySkuIgnoreCase(sku)) {
            return;
        }

        Product product = new Product();

        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setStock(stock);
        product.setMinStock(minStock);
        product.setCategory(category);
        product.setActive(true);

        productRepository.save(product);
    }
    
    private Department createDepartment(
            String name, String description) {

        return departmentRepository.findByNameIgnoreCase(name)
            .orElseGet(() -> departmentRepository.save(
                new Department(name, description)
            ));
    }
}
