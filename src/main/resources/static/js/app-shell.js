
"use strict";

(async () => {

    const $ = id => document.getElementById(id);

    const context = StockFlowContext.read();

    function lockScreen() {

        $("shell-content").hidden = true;
        $("shell-locked").hidden = false;

        $("shell-locked").textContent =
            "No hay un contexto offline vigente. " +
            "Recupera la conexion e inicia sesion.";
    }

    function validContext() {

        const current = StockFlowContext.read();

        return current &&
            current.account === context?.account &&
            current.generation === context?.generation;
    }

    if (!context) {

        lockScreen();
        return;
    }

    function loadScript(path) {

        return new Promise((resolve, reject) => {

            const script = document.createElement("script");

            script.src = path;
            script.onload = resolve;
            script.onerror = reject;

            document.head.appendChild(script);
        });
    }

    // Proporcionar identidad a los modulos existentes.

    const meta = document.createElement("meta");

    meta.name = "stockflow-account";
    meta.content = context.account;

    document.head.appendChild(meta);

    try {

        await loadScript("/js/security.js");
        await loadScript("/js/indexed-db.js");
        await loadScript("/js/offline-data.js");
        await loadScript("/js/sync.js");

        await StockFlowDB.open();

    } catch (error) {

        lockScreen();
        console.error(error);
        return;
    }

    if (!validContext()) {

        lockScreen();
        return;
    }

    const movementView =
        location.pathname === "/movements";

    $("shell-locked").hidden = true;
    $("shell-content").hidden = false;

    $("shell-dashboard").hidden = movementView;
    $("shell-movements").hidden = !movementView;

    $("shell-account").textContent =
        "Sesion local: " + context.account;

    let products = [];
    let departments = [];
    let movements = [];
    let queue = [];
    let stockMap = new Map();

    let coordinates = null;
    let locating = false;
    let saving = false;
    let dirty = false;
    let reconnectPending = false;
    let catalogReady = false;

    function message(text) {

        $("shell-message").textContent = text;
    }

    function addRow(table, values) {

        const row = document.createElement("tr");

        for (const value of values) {

            const cell = document.createElement("td");

            cell.textContent = String(value ?? "—");

            row.appendChild(cell);
        }

        table.appendChild(row);
    }

    function estimatedStock() {

        stockMap = new Map(
            products.map(p => [p.id, p.stock])
        );

        const confirmed = new Set(
            movements.map(m => m.operationUuid)
        );

        for (const item of queue) {

            if (item.status !== "PENDING" ||
                confirmed.has(item.operationUuid)) {

                continue;
            }

            const p = item.payload;

            if (!stockMap.has(p.productId)) continue;

            const delta = p.type === "IN"
                ? p.quantity
                : -p.quantity;

            stockMap.set(
                p.productId,
                stockMap.get(p.productId) + delta
            );
        }
    }

    function renderDashboard() {

        $("shell-products").textContent = products.length;

        $("shell-units").textContent = products.reduce(
            (sum, p) => sum + stockMap.get(p.id), 0
        );

        $("shell-low").textContent = products.filter(
            p => stockMap.get(p.id) <= p.minStock
        ).length;

        $("shell-pending").textContent = queue.length;

        const table = $("shell-product-table");

        table.replaceChildren();

        for (const p of products) {

            addRow(table, [
                p.sku,
                p.name,
                p.categoryName,
                stockMap.get(p.id),
                p.minStock
            ]);
        }
    }

    function renderHistory() {

        const table = $("shell-movement-table");

        table.replaceChildren();

        for (const m of movements) {

            addRow(table, [
                new Date(m.createdAt).toLocaleString("es-MX"),
                m.productName,
                m.type === "IN" ? "Entrada" : "Salida",
                m.quantity,
                `${m.previousStock} → ${m.resultingStock}`,
                m.departmentName || "—"
            ]);
        }
    }

    function renderQueue() {

        const container = $("shell-queue");

        container.replaceChildren();

        if (queue.length === 0) {

            container.textContent =
                "No hay movimientos pendientes.";

            return;
        }

        for (const item of queue) {

            const p = item.payload;

            const product = products.find(
                product => product.id === p.productId
            );

            const row = document.createElement("p");

            const type = p.type === "IN"
                ? "Entrada"
                : "Salida";

            row.textContent =
                `${type} | ${product?.name || p.productId} | ` +
                `${p.quantity} unidades | ${item.status}` +
                (item.lastError
                    ? ` | ${item.lastError}`
                    : "");

            container.appendChild(row);
        }
    }

    function renderOptions() {

        const product = $("shell-product");
        const department = $("shell-department");

        const previousProduct = product.value;
        const previousDepartment = department.value;

        product.replaceChildren(
            new Option("Seleccionar producto", "")
        );

        department.replaceChildren(
            new Option("Sin departamento", "")
        );

        for (const p of products) {

            product.add(new Option(
                `${p.sku} - ${p.name}`, p.id
            ));
        }

        for (const d of departments) {

            department.add(new Option(d.name, d.id));
        }

        product.value = previousProduct;
        department.value = previousDepartment;
    }

    function calculateStock() {

        const productId = Number($("shell-product").value);
        const quantity = Number($("shell-quantity").value);
        const type = $("shell-type").value;

        $("shell-department").required = type === "OUT";

        const current = stockMap.get(productId);

        if (!Number.isInteger(current)) {

            $("shell-estimate").textContent =
                "Selecciona un producto.";

            return false;
        }

        const resulting = type === "IN"
            ? current + quantity
            : current - quantity;

        const valid =
            Number.isInteger(quantity) &&
            quantity > 0 &&
            resulting >= 0 &&
            resulting <= 2147483647;

        $("shell-estimate").textContent =
            `Stock estimado: ${current} → ${resulting}`;

        if (!valid) {

            $("shell-estimate").textContent +=
                " | Cantidad no valida.";
        }

        return valid;
    }

    async function render() {

        if (!validContext()) {

            lockScreen();
            return;
        }

        const [
            cachedProducts,
            cachedDepartments,
            cachedMovements,
            cachedQueue,
            lastSync
        ] = await Promise.all([

            StockFlowOffline.getProducts(),
            StockFlowOffline.getDepartments(),
            StockFlowOffline.getMovements(),
            StockFlowSync.getQueue(),
            StockFlowOffline.getLastSync()
        ]);

        if (!validContext()) {

            lockScreen();
            return;
        }

        products = cachedProducts.filter(
            p => p.active !== false
        );

        departments = cachedDepartments;
        movements = cachedMovements;
        queue = cachedQueue;

        catalogReady = Boolean(lastSync);

        $("shell-last-sync").textContent = lastSync
            ? "Datos actualizados: " +
            new Date(lastSync).toLocaleString("es-MX")
            : "No hay un catalogo local disponible.";

        estimatedStock();

        renderDashboard();
        renderHistory();
        renderQueue();
        renderOptions();
        calculateStock();

        $("shell-save").disabled =
            !catalogReady || saving || locating;
    }

    function reconnect() {

        if (saving || locating || dirty) {

            reconnectPending = true;

            message(
                "Conexion disponible. Guarda el formulario " +
                "antes de regresar a la vista en linea."
            );

            return;
        }

        location.replace(
            movementView ? "/movements" : "/"
        );
    }

    $("shell-form").addEventListener("input", () => {

        dirty = true;
        calculateStock();
    });

    $("shell-form").addEventListener("change", () => {

        dirty = true;
        calculateStock();
    });

    $("shell-location").addEventListener("click", () => {

        if (locating || saving || !validContext()) return;

        if (!navigator.geolocation) {

            $("shell-location-status").textContent =
                "Geolocalizacion no disponible.";

            return;
        }

        locating = true;
        dirty = true;

        coordinates = null;

        $("shell-save").disabled = true;
        $("shell-location").disabled = true;

        $("shell-location-status").textContent =
            "Obteniendo ubicacion...";

        navigator.geolocation.getCurrentPosition(

            position => {

                coordinates = {
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude
                };

                $("shell-location-status").textContent =
                    "Ubicacion registrada.";

                finishLocation();
            },

            () => {

                $("shell-location-status").textContent =
                    "No se obtuvo la ubicacion. " +
                    "Puedes continuar sin ella.";

                finishLocation();
            },

            {
                enableHighAccuracy: true,
                timeout: 15000,
                maximumAge: 0
            }
        );

        function finishLocation() {

            locating = false;

            $("shell-save").disabled = !catalogReady;
            $("shell-location").disabled = false;
        }
    });

    $("shell-form").addEventListener("submit", async event => {

        event.preventDefault();

        if (saving || locating || !catalogReady) return;

        if (!validContext()) {

            lockScreen();
            return;
        }

        saving = true;

        $("shell-save").disabled = true;

        try {

            // Volver a consultar la cola antes de validar.
            queue = await StockFlowSync.getQueue();

            estimatedStock();

            if (!calculateStock() ||
                !$("shell-form").reportValidity()) {

                message(
                    "Verifica los datos y las existencias."
                );

                return;
            }

            if (!validContext()) {

                lockScreen();
                return;
            }

            const payload = {

                operationUuid: crypto.randomUUID(),

                productId: Number($("shell-product").value),

                departmentId: $("shell-department").value
                    ? Number($("shell-department").value)
                    : null,

                type: $("shell-type").value,

                quantity: Number($("shell-quantity").value),

                observations:
                    $("shell-observations").value.trim(),

                latitude: coordinates?.latitude ?? null,

                longitude: coordinates?.longitude ?? null
            };

            await StockFlowSync.enqueue(payload);

            dirty = false;
            coordinates = null;

            $("shell-form").reset();

            $("shell-location-status").textContent =
                "Ubicacion no registrada.";

            message(
                "Movimiento guardado en IndexedDB. " +
                "Pendiente de sincronizacion."
            );

            await render();

        } catch (error) {

            message(
                "No se pudo completar la operacion. " +
                "Revisa la cola antes de reintentar."
            );

            console.error(error);

        } finally {

            saving = false;

            $("shell-save").disabled =
                !catalogReady || locating;

            if (reconnectPending && !dirty) {

                reconnect();
            }
        }
    });

    $("shell-reconnect").addEventListener(
        "click", reconnect
    );

    window.addEventListener("online", reconnect);

    window.addEventListener("storage", () => {

        if (!validContext()) lockScreen();
    });

    window.addEventListener("pageshow", () => {

        if (!validContext()) lockScreen();
    });

    setInterval(() => {

        if (!validContext()) lockScreen();

    }, 5000);

    // Evitar perder un formulario sin guardar.
    window.addEventListener("beforeunload", event => {

        if (dirty || saving) {

            event.preventDefault();
            event.returnValue = "";
        }
    });

    try {

        await render();

    } catch (error) {

        message("Error al consultar los datos locales.");
        console.error(error);
    }

})();
