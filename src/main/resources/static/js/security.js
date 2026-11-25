
"use strict";

const stockflowAccount = document.querySelector(
    'meta[name="stockflow-account"]'
)?.content;

if (!stockflowAccount) {

    throw new Error(
        "No se identifico al usuario autenticado."
    );
}

window.StockFlowAccount = stockflowAccount;

async function stockflowFetch(url, options = {}) {

    const target = new URL(url, window.location.origin);

    if (target.origin !== window.location.origin) {
        throw new Error("Destino no permitido.");
    }

    const method = (
        options.method || "GET"
    ).toUpperCase();

    const headers = new Headers(options.headers || {});
    headers.set(
        "X-StockFlow-Account",
        encodeURIComponent(stockflowAccount)
    );

    const safeMethods = [
        "GET",
        "HEAD",
        "OPTIONS"
    ];

    if (!safeMethods.includes(method)) {

        const csrfResponse = await fetch("/api/csrf", {

            credentials: "same-origin",

            cache: "no-store",

            headers: {
                "X-StockFlow-Account":
                    encodeURIComponent(stockflowAccount)
            },

            signal: AbortSignal.timeout(10000)
        });

        if (csrfResponse.status === 401) {

            throw new Error(
                "La sesión expiró. Inicia sesión nuevamente."
            );
        }

        if (!csrfResponse.ok) {

            throw new Error(
                "No se pudo obtener el token de seguridad."
            );
        }

        const csrf = await csrfResponse.json();

        headers.set(csrf.headerName, csrf.token);
    }

    return fetch(target.href, {
        ...options,
        headers,
        credentials: "same-origin",
        cache: "no-store"
    });
}

window.stockflowFetch = stockflowFetch;
