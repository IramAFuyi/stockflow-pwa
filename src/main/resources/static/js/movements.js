
"use strict";

const modal = document.getElementById("movement-modal");
const form = document.getElementById("movement-form");
const fieldset = document.getElementById("movement-fields");
const saveButton = document.getElementById("save-movement");
const errorMessage = document.getElementById("movement-error");

const closeButton = document.getElementById("close-movement");
const cancelButton = document.getElementById("cancel-movement");

const locationStatus =
    document.getElementById("location-status");

const locationButton =
    document.getElementById("get-location");

const clearLocationButton =
    document.getElementById("clear-location");

const fields = {
    product: document.getElementById("movement-product"),
    type: document.getElementById("movement-type"),
    quantity: document.getElementById("movement-quantity"),
    department: document.getElementById("movement-department"),
    observations: document.getElementById("movement-observations"),
    currentStock: document.getElementById("current-stock"),
    resultingStock: document.getElementById("resulting-stock")
};

let coordinates = null;
let pendingPayload = null;
let sending = false;
let locationRequest = 0;
let locating = false;

// ===================================
// Mensajes y validaciones
// ===================================

function showError(message) {
    errorMessage.textContent = message;
    errorMessage.hidden = false;
}

function clearError() {
    errorMessage.textContent = "";
    errorMessage.hidden = true;
}

function calculateStock() {

    fields.department.required = fields.type.value === "OUT";

    const selected = fields.product.selectedOptions[0];

    const quantity = Number(fields.quantity.value);

    if (!fields.product.value || !selected) {

        fields.currentStock.value = "";
        fields.resultingStock.value = "";
        fields.resultingStock.classList.remove("invalid-stock");

        return false;
    }

    const currentStock = Number(selected.dataset.stock);

    const resultingStock = fields.type.value === "IN"
        ? currentStock + quantity
        : currentStock - quantity;

    fields.currentStock.value = currentStock;

    fields.currentStock.title =
        "Existencias estimadas incluyendo operaciones pendientes";

    const valid =
        Number.isInteger(currentStock) &&
        Number.isInteger(quantity) &&
        quantity > 0 &&
        resultingStock >= 0 &&
        resultingStock <= 2147483647;

    fields.resultingStock.value = resultingStock;

    fields.resultingStock.classList.toggle(
        "invalid-stock", !valid
    );

    return valid;
}

fields.product.addEventListener("change", calculateStock);
fields.type.addEventListener("change", calculateStock);
fields.quantity.addEventListener("input", calculateStock);

// ===================================
// Geolocalizacion
// ===================================

function resetLocation() {

    locationRequest++;
    locating = false;
    coordinates = null;

    locationButton.disabled = false;

    locationStatus.className = "";
    locationStatus.textContent = "Ubicación no registrada.";
}

locationButton.addEventListener("click", () => {

    if (!navigator.geolocation) {

        locationStatus.textContent =
            "Este navegador no admite geolocalización.";

        return;
    }

    if (!window.isSecureContext) {

        locationStatus.textContent =
            "La geolocalización requiere HTTPS o localhost.";

        return;
    }

    resetLocation();

    const requestId = ++locationRequest;

    locating = true;
    locationButton.disabled = true;
    saveButton.disabled = true;

    locationStatus.textContent = "Obteniendo ubicación...";

    navigator.geolocation.getCurrentPosition(

        position => {

            if (requestId !== locationRequest) return;

            coordinates = {
                latitude: position.coords.latitude,
                longitude: position.coords.longitude
            };

            locationStatus.className = "location-success";

            locationStatus.textContent =
                `Ubicación obtenida: ` +
                `${coordinates.latitude.toFixed(6)}, ` +
                `${coordinates.longitude.toFixed(6)}. ` +
                `Precisión aproximada: ` +
                `${Math.round(position.coords.accuracy)} m.`;

            locating = false;
            locationButton.disabled = false;
            saveButton.disabled = false;
        },

        error => {

            if (requestId !== locationRequest) return;

            const messages = {
                1: "Permiso de ubicación denegado.",
                2: "Ubicación no disponible.",
                3: "Tiempo de espera agotado."
            };

            locationStatus.className = "location-error";

            locationStatus.textContent =
                (messages[error.code] || "No se obtuvo la ubicación.") +
                " Puedes continuar sin ella.";

            locating = false;
            locationButton.disabled = false;
            saveButton.disabled = false;
        },

        {
            enableHighAccuracy: true,
            timeout: 15000,
            maximumAge: 0
        }
    );
});

clearLocationButton.addEventListener("click", () => {
    resetLocation();
    saveButton.disabled = false;
});

// ===================================
// Formulario de movimientos
// ===================================

async function openMovement() {

    if (pendingPayload) {
        modal.showModal();
        return;
    }

    form.reset();
    clearError();
    resetLocation();

    fieldset.disabled = false;
    saveButton.disabled = false;

    saveButton.textContent = "Registrar movimiento";

    await refreshMovementEstimates();

    calculateStock();

    modal.showModal();
}

function closeMovement() {

    if (sending || pendingPayload) return;

    resetLocation();
    modal.close();
}

document.getElementById("new-movement")
    .addEventListener("click", openMovement);

closeButton.addEventListener("click", closeMovement);
cancelButton.addEventListener("click", closeMovement);

modal.addEventListener("cancel", event => {

    if (sending || pendingPayload) {
        event.preventDefault();
    } else {
        resetLocation();
    }
});

function lockMovement(locked) {

    fieldset.disabled = locked;
    cancelButton.disabled = locked;
    closeButton.disabled = locked;
}


form.addEventListener("submit", async event => {

    event.preventDefault();

    if (sending || locating) return;

    clearError();

    if (!pendingPayload) {

        if (!calculateStock()) {

            showError(
                "Verifica la cantidad y las existencias disponibles."
            );

            return;
        }

        pendingPayload = {

            operationUuid: crypto.randomUUID(),

            productId: Number(fields.product.value),

            departmentId: fields.department.value
                ? Number(fields.department.value)
                : null,

            type: fields.type.value,

            quantity: Number(fields.quantity.value),

            observations: fields.observations.value.trim(),

            latitude: coordinates?.latitude ?? null,

            longitude: coordinates?.longitude ?? null
        };
    }

    sending = true;

    lockMovement(true);

    saveButton.disabled = true;
    saveButton.textContent = "Guardando...";

    try {

        // Guardar primero en IndexedDB.
        await StockFlowSync.enqueue(pendingPayload);

        const operationUuid = pendingPayload.operationUuid;

        pendingPayload = null;

        modal.close();

        form.reset();

        resetLocation();

        lockMovement(false);

        // Intentar enviar al servidor.
        if (navigator.onLine) {

            await StockFlowSync.synchronize();
        }

        const remaining = await StockFlowDB.get(
            "pendingMovements",
            operationUuid
        );

        if (!remaining) {

            alert("Movimiento registrado correctamente.");

            window.location.reload();

        } else if (remaining.status === "CONFLICT") {

            alert(
                "El servidor rechazo el movimiento. " +
                "Consulta el panel de sincronizacion."
            );

        } else {

            alert(
                "Movimiento guardado en el dispositivo. " +
                "Se sincronizara al recuperar la conexion."
            );
        }

    } catch (error) {

        if (modal.open) {

            showError(
                "No se pudo guardar el movimiento: " +
                error.message
            );

        } else {

            alert(
                "Consulta la cola antes de repetir la operacion."
            );
        }

    } finally {

        sending = false;
        saveButton.disabled = false;

        saveButton.textContent = pendingPayload
            ? "Reintentar movimiento"
            : "Registrar movimiento";

        lockMovement(Boolean(pendingPayload));
    }
});

// ===================================
// Filtros del historial
// ===================================

const movementSearch =
    document.getElementById("movement-search");

const movementFilter =
    document.getElementById("movement-filter");

const movementRows =
    document.querySelectorAll("#movement-table tr");

function normalize(text) {

    return text.toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "");
}

function filterMovements() {

    const query = normalize(movementSearch.value.trim());
    const type = movementFilter.value;

    let visible = 0;

    movementRows.forEach(row => {

        const matchesText =
            normalize(row.textContent).includes(query);

        const matchesType =
            type === "all" || row.dataset.type === type;

        row.hidden = !(matchesText && matchesType);

        if (!row.hidden) visible++;
    });

    document.getElementById("result-count").textContent =
        `${visible} de ${movementRows.length} movimientos`;

    document.getElementById("empty-message").hidden =
        visible !== 0;
}

movementSearch.addEventListener("input", filterMovements);
movementFilter.addEventListener("change", filterMovements);

// Formatear fecha UTC a hora local.
document.querySelectorAll(".movement-date").forEach(cell => {

    const date = new Date(cell.textContent.trim());

    if (!Number.isNaN(date.getTime())) {

        cell.textContent = date.toLocaleString("es-MX", {
            dateStyle: "short",
            timeStyle: "short"
        });
    }
});

// ===================================
// Estado de conexion
// ===================================

function updateConnectionStatus() {

    const status =
        document.getElementById("connection-status");

    status.textContent = navigator.onLine
        ? "● Conectado a la red"
        : "● Sin conexión";

    status.classList.toggle("online", navigator.onLine);
    status.classList.toggle("offline", !navigator.onLine);
}

window.addEventListener("online", updateConnectionStatus);
window.addEventListener("offline", updateConnectionStatus);

// ===================================
// Menu lateral
// ===================================

const sidebar = document.querySelector(".sidebar");
const menuToggle = document.getElementById("menu-toggle");

function closeMenu() {

    sidebar.classList.remove("open");

    menuToggle.setAttribute("aria-expanded", "false");
}

menuToggle.addEventListener("click", () => {

    const opened = sidebar.classList.toggle("open");

    menuToggle.setAttribute("aria-expanded", String(opened));
});

document.addEventListener("click", event => {

    if (!sidebar.contains(event.target) &&
        !menuToggle.contains(event.target)) {

        closeMenu();
    }
});

document.addEventListener("keydown", event => {

    if (event.key === "Escape") closeMenu();
});

filterMovements();
updateConnectionStatus();


async function updateLocalStock() {

    const products = await StockFlowOffline.getProducts();

    const departments =
        await StockFlowOffline.getDepartments();

    const queue = await StockFlowSync.getQueue();

    const stockMap = new Map(
        products.map(p => [p.id, p.stock])
    );

    for (const movement of queue) {

        const payload = movement.payload;

        if (!stockMap.has(payload.productId)) {
            continue;
        }

        const previous = stockMap.get(
            payload.productId
        );

        const quantity = payload.type === "IN"
            ? payload.quantity
            : -payload.quantity;

        stockMap.set(
            payload.productId,
            previous + quantity
        );
    }

    const selectedProduct = fields.product.value;

    const selectedDepartment = fields.department.value;

    if (products.length > 0) {

        fields.product.replaceChildren(
            new Option("Seleccionar producto", "")
        );

        for (const product of products) {

            const option = new Option(
                `${product.sku} - ${product.name}`,
                product.id
            );

            option.dataset.stock =
                stockMap.get(product.id);

            fields.product.appendChild(option);
        }

        fields.product.value = selectedProduct;
    }

    if (departments.length > 0) {

        fields.department.replaceChildren(
            new Option("Sin departamento", "")
        );

        for (const department of departments) {

            fields.department.appendChild(
                new Option(
                    department.name,
                    department.id
                )
            );
        }

        fields.department.value = selectedDepartment;
    }

    calculateStock();
}

async function refreshMovementEstimates() {

    try {

        if (!sending && !pendingPayload) {

            await updateLocalStock();
        }

    } catch (error) {

        console.warn(
            "[StockFlow] No se actualizaron las estimaciones",
            error
        );
    }
}

window.addEventListener(
    "stockflow:queue-changed",
    refreshMovementEstimates
);

window.addEventListener(
    "stockflow:sync-completed",
    refreshMovementEstimates
);

window.addEventListener(
    "load",
    refreshMovementEstimates
);
