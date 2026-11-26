
"use strict";

const StockFlowSync = (() => {

    let syncing = false;
    let localLock = Promise.resolve();

    async function notifyChanges() {

        window.dispatchEvent(
            new Event("stockflow:queue-changed")
        );
    }

    // Usar el mismo bloqueo para enviar, guardar
    // y resolver operaciones.

    function withQueueLock(action) {

        if (navigator.locks) {

            return navigator.locks.request(
                "stockflow-sync",
                action
            );
        }

        const task = localLock.then(action);

        localLock = task.catch(() => { });

        return task;
    }

    function sortMovements(movements) {

        return movements.sort(
            (a, b) =>
                new Date(a.createdAt) -
                new Date(b.createdAt)
        );
    }

    async function enqueue(payload) {

        return withQueueLock(async () => {

            const existing = await StockFlowDB.get(
                "pendingMovements",
                payload.operationUuid
            );

            if (existing) {

                if (existing.status === "DISCARDED") {

                    throw new Error(
                        "La operacion ya fue descartada."
                    );
                }

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
        });
    }

    async function getQueue() {

        const movements = await StockFlowDB.getAll(
            "pendingMovements"
        );

        return sortMovements(
            movements.filter(m =>
                m.status !== "DISCARDED"
            )
        );
    }

    async function getDiscarded() {

        const movements = await StockFlowDB.getAll(
            "pendingMovements"
        );

        return movements
            .filter(m => m.status === "DISCARDED")
            .sort((a, b) =>
                new Date(b.discardedAt) -
                new Date(a.discardedAt)
            );
    }

    // Resolver un conflicto sin modificar su payload.

    async function resolveConflict(operationUuid, action) {

        if (!navigator.onLine) {

            throw new Error(
                "Recupera la conexion para resolver el conflicto."
            );
        }

        if (!["retry", "discard"].includes(action)) {

            throw new Error("Accion no valida.");
        }

        return withQueueLock(async () => {

            const movement = await StockFlowDB.get(
                "pendingMovements",
                operationUuid
            );

            if (!movement ||
                movement.status !== "CONFLICT") {

                throw new Error(
                    "La operacion ya no esta en conflicto."
                );
            }

            // Comprobar que la sesion sigue autenticada
            // y corresponde a la cuenta actual.

            const session = await stockflowFetch(
                "/api/csrf",
                {
                    signal: AbortSignal.timeout(10000)
                }
            );

            if (!session.ok) {

                throw new Error(
                    "La sesion no es valida. Inicia sesion nuevamente."
                );
            }

            if (action === "retry") {

                movement.previousError = movement.lastError;
                movement.lastError = null;
                movement.status = "PENDING";

            } else {

                movement.status = "DISCARDED";

                movement.discardedAt =
                    new Date().toISOString();

                movement.discardedBy =
                    window.StockFlowAccount;
            }

            await StockFlowDB.put(
                "pendingMovements",
                movement
            );

            await notifyChanges();
        });
    }

    // Estimacion compartida entre SSR y App Shell.

    function estimateStock(products, queue, history = []) {

        const stockMap = new Map(
            products.map(p => [p.id, p.stock])
        );

        const confirmed = new Set(
            history.map(m => m.operationUuid)
        );

        for (const movement of queue) {

            // Mantener el orden de la cola.
            // No estimar operaciones bloqueadas.

            if (movement.status === "CONFLICT") {

                break;
            }

            if (movement.status !== "PENDING" ||
                confirmed.has(movement.operationUuid)) {

                continue;
            }

            const payload = movement.payload;

            if (!stockMap.has(payload.productId)) {

                break;
            }

            const previous = stockMap.get(
                payload.productId
            );

            const delta = payload.type === "IN"
                ? payload.quantity
                : -payload.quantity;

            const resulting = previous + delta;

            if (!Number.isInteger(resulting) ||
                resulting < 0 ||
                resulting > 2147483647) {

                break;
            }

            stockMap.set(
                payload.productId,
                resulting
            );
        }

        return stockMap;
    }

    async function sendMovement(movement) {

        movement.attempts++;

        await StockFlowDB.put(
            "pendingMovements",
            movement
        );

        try {

            const response = await stockflowFetch(
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

                movement.lastHttpStatus =
                    response.status;

                if ([400, 404, 409, 422].includes(
                    response.status
                )) {

                    movement.status = "CONFLICT";

                    movement.conflictAt =
                        new Date().toISOString();

                } else {

                    movement.status = "PENDING";
                }

                await StockFlowDB.put(
                    "pendingMovements",
                    movement
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
        let conflictDetected = false;

        try {

            const movements = await getQueue();

            for (const movement of movements) {

                if (!navigator.onLine ||
                    movement.status === "CONFLICT") {

                    break;
                }

                const success =
                    await sendMovement(movement);

                if (!success) {

                    conflictDetected =
                        movement.status === "CONFLICT";

                    break;
                }

                confirmed++;
            }

            // Actualizar existencias incluso si el
            // servidor rechazo una operacion.

            if (confirmed > 0 || conflictDetected) {

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

            return await withQueueLock(processQueue);

        } finally {

            syncing = false;
        }
    }

    return {
        enqueue,
        getQueue,
        getDiscarded,
        resolveConflict,
        estimateStock,
        synchronize
    };

})();

window.StockFlowSync = StockFlowSync;
