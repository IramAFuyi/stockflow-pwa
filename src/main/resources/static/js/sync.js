
"use strict";

const StockFlowSync = (() => {

    let syncing = false;

    async function notifyChanges() {

        window.dispatchEvent(
            new Event("stockflow:queue-changed")
        );
    }

    // Guardar antes de intentar enviar.
    async function enqueue(payload) {

        const existing = await StockFlowDB.get(
            "pendingMovements",
            payload.operationUuid
        );

        if (existing) {
            return existing;
        }

        const movement = {

            operationUuid: payload.operationUuid,

            payload: payload,

            status: "PENDING",

            attempts: 0,

            createdAt: new Date().toISOString(),

            lastError: null
        };

        await StockFlowDB.put(
            "pendingMovements",
            movement
        );

        await notifyChanges();

        return movement;
    }

    async function getQueue() {

        const movements = await StockFlowDB.getAll(
            "pendingMovements"
        );

        return movements.sort(
            (a, b) =>
                new Date(a.createdAt) -
                new Date(b.createdAt)
        );
    }

    async function sendMovement(movement) {

        movement.attempts++;

        await StockFlowDB.put(
            "pendingMovements", movement
        );

        try {

            const response = await fetch(
                "/api/movements",
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json"
                    },

                    body: JSON.stringify(movement.payload),

                    signal: AbortSignal.timeout(15000)
                }
            );

            if (!response.ok) {

                const error = await response.json()
                    .catch(() => ({}));

                movement.lastError =
                    error.message ||
                    `Error HTTP ${response.status}`;

                // Errores que requieren revision manual.
                if ([400, 404, 409, 422].includes(
                    response.status
                )) {

                    movement.status = "CONFLICT";

                } else {

                    movement.status = "PENDING";
                }

                await StockFlowDB.put(
                    "pendingMovements", movement
                );

                return false;
            }

            const confirmed = await response.json();

            if (confirmed.operationUuid !==
                movement.operationUuid) {

                throw new Error(
                    "La confirmacion no coincide con el UUID"
                );
            }

            // El servidor confirmo la operacion.
            await StockFlowDB.remove(
                "pendingMovements",
                movement.operationUuid
            );

            console.log(
                "[StockFlow] Movimiento sincronizado:",
                confirmed.operationUuid
            );

            return true;

        } catch (error) {

            movement.status = "PENDING";
            movement.lastError = error.message;

            await StockFlowDB.put(
                "pendingMovements",
                movement
            );

            return false;
        }
    }

    async function processQueue() {

        let confirmed = 0;

        try {

            const movements = await getQueue();

            for (const movement of movements) {

                // Detener la cola ante un conflicto.
                if (movement.status === "CONFLICT") {
                    break;
                }

                const success =
                    await sendMovement(movement);

                if (!success) {
                    break;
                }

                confirmed++;
            }

            if (confirmed > 0) {

                await StockFlowOffline.refreshCatalogs();
            }

            return confirmed;

        } finally {

            await notifyChanges();
        }
    }

    async function synchronize() {

        if (syncing || !navigator.onLine) {
            return 0;
        }

        syncing = true;

        try {

            // Evitar sincronizaciones simultaneas entre pestañas.
            if (navigator.locks) {

                return await navigator.locks.request(
                    "stockflow-sync",
                    () => processQueue()
                );
            }

            return await processQueue();

        } finally {

            syncing = false;
        }
    }

    return {
        enqueue,
        getQueue,
        synchronize
    };

})();

window.StockFlowSync = StockFlowSync;
