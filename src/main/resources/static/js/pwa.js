
"use strict";

if ("serviceWorker" in navigator) {

    window.addEventListener("load", async () => {

        try {

            const registration =
                await navigator.serviceWorker.register("/sw.js");

            console.log(
                "[StockFlow] Service Worker registrado:",
                registration.scope
            );

        } catch (error) {

            console.error(
                "[StockFlow] Error al registrar Service Worker:",
                error
            );
        }
    });
}

function updateOfflineBanner() {

    const banner =
        document.getElementById("offline-banner");

    if (banner) {

        banner.hidden = navigator.onLine;
    }
}

window.addEventListener("online", updateOfflineBanner);

window.addEventListener("offline", updateOfflineBanner);

updateOfflineBanner();


// ===================================
// IndexedDB y sincronizacion
// ===================================

let resolvingConflict = false;

async function handleConflict(operationUuid, action) {

    if (resolvingConflict) return;

    if (action === "discard") {

        const accepted = confirm(
            "¿Deseas descartar este movimiento? " +
            "No se enviara al servidor, pero se conservara " +
            "como evidencia local."
        );

        if (!accepted) return;
    }

    resolvingConflict = true;

    try {

        await StockFlowSync.resolveConflict(
            operationUuid,
            action
        );

        await runSynchronization();

    } catch (error) {

        alert(error.message);
        console.error(error);

    } finally {

        resolvingConflict = false;

        await updateSyncPanel();
    }
}

async function updateSyncPanel() {

    const status =
        document.getElementById("sync-status");

    const list =
        document.getElementById("pending-list");

    const lastSync =
        document.getElementById("last-sync");

    if (!status || !list || !lastSync) {
        return;
    }

    try {

        const queue = await StockFlowSync.getQueue();

        const products =
            await StockFlowOffline.getProducts();

        const pending = queue.filter(
            m => m.status === "PENDING"
        ).length;

        const conflicts = queue.filter(
            m => m.status === "CONFLICT"
        ).length;

        status.textContent = queue.length === 0

            ? "No hay movimientos pendientes."

            : `${pending} pendientes | ` +
            `${conflicts} requieren revision`;

        list.replaceChildren();

        for (const movement of queue) {

            const product = products.find(
                p => p.id === movement.payload.productId
            );

            const item = document.createElement("div");

            item.className =
                "pending-item " +
                movement.status.toLowerCase();

            const title = document.createElement("strong");

            const type = movement.payload.type === "IN"
                ? "Entrada"
                : "Salida";

            title.textContent =
                `${type} - ${product?.name ||
                "Producto " + movement.payload.productId}`;

            const detail = document.createElement("p");

            detail.textContent =
                `Cantidad: ${movement.payload.quantity} | ` +
                `Estado: ${movement.status}`;

            detail.className = "pending-detail";

            item.append(title, detail);

            if (movement.lastError) {

                const error = document.createElement("p");

                error.textContent = movement.lastError;

                error.className = "pending-detail";

                item.appendChild(error);
            }

            if (movement.status === "CONFLICT") {

                const actions = document.createElement("div");

                actions.className = "conflict-actions";

                const retry = document.createElement("button");

                retry.type = "button";
                retry.className = "btn-secondary";
                retry.textContent = "Reintentar";

                retry.disabled =
                    resolvingConflict || !navigator.onLine;

                retry.addEventListener("click", () => {

                    handleConflict(
                        movement.operationUuid,
                        "retry"
                    );
                });

                const discard = document.createElement("button");

                discard.type = "button";
                discard.className = "btn-discard";
                discard.textContent = "Descartar";

                discard.disabled =
                    resolvingConflict || !navigator.onLine;

                discard.addEventListener("click", () => {

                    handleConflict(
                        movement.operationUuid,
                        "discard"
                    );
                });

                actions.append(retry, discard);

                item.appendChild(actions);
            }

            list.appendChild(item);
        }

        const discarded = await StockFlowSync.getDiscarded();

        if (discarded.length > 0) {

            const archive = document.createElement("details");

            archive.className = "discarded-section";

            const heading = document.createElement("summary");

            heading.textContent =
                `Movimientos descartados (${discarded.length})`;

            archive.appendChild(heading);

            for (const movement of discarded) {

                const row = document.createElement("div");

                row.className = "discarded-item";

                const product = products.find(
                    p => p.id === movement.payload.productId
                );

                const title = document.createElement("strong");

                title.textContent =
                    product?.name ||
                    `Producto ${movement.payload.productId}`;

                const detail = document.createElement("p");

                detail.textContent =
                    `${movement.payload.type} | ` +
                    `${movement.payload.quantity} unidades | ` +
                    `${movement.discardedBy || "Sin usuario"}`;

                const reason = document.createElement("p");

                reason.textContent =
                    "Motivo: " +
                    (movement.lastError || "Cancelado manualmente");

                const date = document.createElement("small");

                date.textContent =
                    new Date(movement.discardedAt)
                        .toLocaleString("es-MX");

                row.append(title, detail, reason, date);

                archive.appendChild(row);
            }

            list.appendChild(archive);
        }

        const date = await StockFlowOffline.getLastSync();

        lastSync.textContent = date

            ? "Ultima actualizacion del catalogo: " +
            new Date(date).toLocaleString("es-MX")

            : "Catalogo local sin actualizar";

    } catch (error) {

        status.textContent =
            "No se pudo consultar el almacenamiento local.";

        console.error(error);
    }
}

let syncRunning = false;

async function runSynchronization() {

    if (syncRunning || !navigator.onLine) return;

    syncRunning = true;

    const button =
        document.getElementById("sync-now");

    if (button) {
        button.disabled = true;
        button.textContent = "Sincronizando...";
    }

    try {

        const confirmed =
            await StockFlowSync.synchronize();

        if (confirmed > 0) {

            console.log(
                `[StockFlow] ${confirmed} movimientos sincronizados`
            );

            window.dispatchEvent(
                new Event("stockflow:sync-completed")
            );
        }

    } catch (error) {

        console.error(
            "[StockFlow] Error de sincronizacion", error
        );

    } finally {

        syncRunning = false;

        await updateSyncPanel();

        if (button) {
            button.disabled = false;
            button.textContent = "Sincronizar ahora";
        }
    }
}

async function initializeOfflineStorage() {

    try {

        await StockFlowDB.open();

        if (navigator.onLine) {

            await StockFlowOffline.refreshCatalogs();

            await runSynchronization();
        }

        await updateSyncPanel();

    } catch (error) {

        console.error(
            "[StockFlow] Error al iniciar IndexedDB",
            error
        );
    }
}

window.addEventListener(
    "stockflow:queue-changed",
    updateSyncPanel
);

window.addEventListener("online", async () => {

    await StockFlowOffline.refreshCatalogs();

    await runSynchronization();
});

document.getElementById("sync-now")
    ?.addEventListener("click", runSynchronization);

window.addEventListener(
    "load",
    initializeOfflineStorage
);

// Reintentar si el servidor vuelve a estar disponible.
setInterval(() => {

    if (navigator.onLine) {

        runSynchronization();
    }

}, 30000);

// Actualizar las vistas SSR despues de sincronizar.

let refreshPending = false;

function refreshSyncedView() {

    // Evitar perder datos de formularios abiertos.
    if (document.querySelector("dialog[open]")) {

        refreshPending = true;

        return;
    }

    refreshPending = false;

    window.location.reload();
}

window.addEventListener(
    "stockflow:sync-completed",
    refreshSyncedView
);

document.querySelectorAll("dialog").forEach(dialog => {

    dialog.addEventListener("close", () => {

        if (refreshPending) {

            refreshSyncedView();
        }
    });
});


async function detachPushSubscription() {

    if (!("serviceWorker" in navigator) ||
        !("PushManager" in window)) {

        return;
    }

    const registration =
        await navigator.serviceWorker.getRegistration();

    const subscription =
        await registration?.pushManager.getSubscription();

    if (!subscription) return;

    const response = await stockflowFetch(
        "/api/push/subscribe",
        {
            method: "DELETE",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify({
                endpoint: subscription.endpoint
            }),

            signal: AbortSignal.timeout(10000)
        }
    );

    if (!response.ok) {

        throw new Error(
            "No se pudieron desvincular las notificaciones."
        );
    }
}

// ===================================
// Cierre seguro de sesion
// ===================================

let logoutInProgress = false;

document.addEventListener("submit", async event => {

    const form = event.target;

    if (!(form instanceof HTMLFormElement)) return;

    if (form.id !== "logout-form") return;

    event.preventDefault();

    if (logoutInProgress) return;

    if (!navigator.onLine) {

        alert(
            "No puedes cerrar sesion sin conexion."
        );

        return;
    }

    logoutInProgress = true;

    try {

        if (syncRunning || resolvingConflict) {
            alert(
                "Hay una sincronizacion en proceso."
            );

            return;
        }

        const queue = await StockFlowSync.getQueue();

        if (queue.length > 0) {

            alert(
                `Tienes ${queue.length} movimientos pendientes. ` +
                "Debes sincronizarlos antes de cerrar sesion."
            );

            return;
        }

        await detachPushSubscription();

        StockFlowContext.revoke();

        HTMLFormElement.prototype.submit.call(form);

    } catch (error) {

        console.error(error);

        alert(
            "No se pudo cerrar sesion: " + error.message
        );

    } finally {

        logoutInProgress = false;
    }

});

// Bloquear vistas antiguas al cambiar de cuenta.

window.addEventListener("storage", event => {

    if (event.key === "stockflow-account-generation") {

        const context = StockFlowContext.read();

        if (!context ||
            context.account !== window.StockFlowAccount) {

            document.body.hidden = true;

            location.replace("/login");
        }
    }
});

window.addEventListener("pageshow", event => {

    if (event.persisted) {

        const context = StockFlowContext.read();

        if (!context ||
            context.account !== window.StockFlowAccount) {

            location.reload();
        }
    }

    window.addEventListener("offline", updateSyncPanel);
    window.addEventListener("online", updateSyncPanel);
});