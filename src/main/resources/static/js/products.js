
const productModal = document.getElementById("product-modal");
const productForm = document.getElementById("product-form");
const modalTitle = document.getElementById("modal-title");
const formError = document.getElementById("form-error");
const saveProduct = document.getElementById("save-product");

const fields = {
    id: document.getElementById("product-id"),
    version: document.getElementById("product-version"),
    sku: document.getElementById("product-sku"),
    name: document.getElementById("product-name"),
    description: document.getElementById("product-description"),
    category: document.getElementById("product-category"),
    stock: document.getElementById("product-stock"),
    minStock: document.getElementById("product-min-stock")
};

// Peticiones hacia la API REST
async function productRequest(url, options = {}) {

    const response = await fetch(url, options);

    if (!response.ok) {

        const error = await response.json().catch(() => ({}));

        throw new Error(
            error.message || "No se pudo completar la operacion"
        );
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

function showFormError(message) {
    formError.textContent = message;
    formError.hidden = false;
}

function clearFormError() {
    formError.textContent = "";
    formError.hidden = true;
}

// Abrir formulario de registro
function openNewProduct() {

    productForm.reset();
    clearFormError();

    fields.id.value = "";
    fields.version.value = "";
    fields.stock.value = 0;
    fields.minStock.value = 5;

    modalTitle.textContent = "Registrar producto";

    productModal.showModal();
}

// Abrir formulario de edicion
async function openEditProduct(id) {

    try {

        const product = await productRequest(
            `/api/products/${id}`
        );

        productForm.reset();
        clearFormError();

        fields.id.value = product.id;
        fields.version.value = product.version;
        fields.sku.value = product.sku;
        fields.name.value = product.name;
        fields.description.value = product.description || "";
        fields.category.value = product.categoryId;
        fields.stock.value = product.stock;
        fields.minStock.value = product.minStock;

        modalTitle.textContent = "Editar producto";

        productModal.showModal();

    } catch (error) {

        alert(error.message);
    }
}

// Registrar o actualizar producto
productForm.addEventListener("submit", async event => {

    event.preventDefault();
    clearFormError();

    const id = fields.id.value;

    const data = {
        sku: fields.sku.value.trim(),
        name: fields.name.value.trim(),
        description: fields.description.value.trim(),
        categoryId: Number(fields.category.value),
        minStock: Number(fields.minStock.value),
        version: id ? Number(fields.version.value) : null
    };

    saveProduct.disabled = true;
    saveProduct.textContent = "Guardando...";

    try {

        const url = id
            ? `/api/products/${id}`
            : "/api/products";

        await productRequest(url, {
            method: id ? "PUT" : "POST",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify(data)
        });

        productModal.close();

        window.location.reload();

    } catch (error) {

        showFormError(error.message);

    } finally {

        saveProduct.disabled = false;
        saveProduct.textContent = "Guardar producto";
    }
});

// Desactivar producto
async function deactivateProduct(id) {

    const confirmed = confirm(
        "¿Deseas desactivar este producto? " +
        "Su informacion se conservara en la base de datos."
    );

    if (!confirmed) {
        return;
    }

    try {

        await productRequest(`/api/products/${id}`, {
            method: "DELETE"
        });

        window.location.reload();

    } catch (error) {

        alert(error.message);
    }
}

// Eventos
document.getElementById("new-product")
    .addEventListener("click", openNewProduct);

document.getElementById("close-modal")
    .addEventListener("click", () => productModal.close());

document.getElementById("cancel-product")
    .addEventListener("click", () => productModal.close());

document.querySelectorAll(".btn-edit").forEach(button => {

    button.addEventListener("click", () => {
        openEditProduct(button.dataset.id);
    });
});

document.querySelectorAll(".btn-delete").forEach(button => {

    button.addEventListener("click", () => {
        deactivateProduct(button.dataset.id);
    });
});
