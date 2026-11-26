
const CACHE_NAME = "stockflow-v13";

const STATIC_RESOURCES = [
    "/offline.html",
    "/manifest.json",
    "/css/styles.css",
    "/js/dashboard.js",
    "/js/products.js",
    "/js/movements.js",
    "/js/pwa.js",
    "/icons/icon.svg",
    "/icons/icon-192.png",
    "/icons/icon-512.png",
    "/icons/icon-maskable-512.png",
    "/js/indexed-db.js",
    "/js/offline-data.js",
    "/js/sync.js",
    "/js/push.js",
    "/js/security.js",
    "/app-shell.html",
    "/js/offline-context.js",
    "/js/app-shell.js",
];

const SSR_PAGES = [
    "/",
    "/movements"
];

// ===================================
// Instalacion
// ===================================

self.addEventListener("install", event => {

    event.waitUntil(

        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(STATIC_RESOURCES))
    );
});

// ===================================
// Activacion
// ===================================

self.addEventListener("activate", event => {

    event.waitUntil((async () => {

        const keys = await caches.keys();

        await Promise.all(

            keys
                .filter(key =>
                    key.startsWith("stockflow-") &&
                    key !== CACHE_NAME
                )
                .map(key => caches.delete(key))
        );

        await self.clients.claim();

    })());
});

// ===================================
// Cache First
// ===================================

async function cacheFirst(request) {

    const cache = await caches.open(CACHE_NAME);

    const cached = await cache.match(request);

    if (cached) {
        return cached;
    }

    const response = await fetch(request);

    if (response.ok) {

        await cache.put(request, response.clone());
    }

    return response;
}

// ===================================
// Network First
// ===================================

async function networkFirst(request) {

    const cache = await caches.open(CACHE_NAME);

    try {

        const response = await fetch(request);

        if (response.ok &&
            !response.redirected &&
            response.status === 200) {

            await cache.put(request, response.clone());
        }

        return response;

    } catch (error) {

        const cached = await cache.match(request);

        if (cached) {
            return cached;
        }

        if (request.mode === "navigate") {

            return await cache.match("/offline.html");
        }

        return Response.error();
    }
}

// ===================================
// Interceptar peticiones
// ===================================

self.addEventListener("fetch", event => {

    const request = event.request;
    const url = new URL(request.url);

    // No intervenir peticiones externas.
    if (url.origin !== self.location.origin) {
        return;
    }

    // Nunca almacenar operaciones de escritura.
    if (request.method !== "GET") {
        return;
    }

    // Las peticiones REST requieren conexion.
    if (url.pathname.startsWith("/api/") ||
        url.pathname.startsWith("/actuator/")) {

        return;
    }

    // Navegacion SSR con respaldo App Shell.
    if (request.mode === "navigate") {

        event.respondWith((async () => {

            try {

                return await fetch(request, {
                    cache: "no-store"
                });

            } catch (error) {

                const cache = await caches.open(CACHE_NAME);

                if (SSR_PAGES.includes(url.pathname)) {

                    return await cache.match("/app-shell.html")
                        || await cache.match("/offline.html")
                        || Response.error();
                }

                return await cache.match("/offline.html")
                    || Response.error();
            }

        })());

        return;
    }

    // Manifiesto.
    if (url.pathname === "/manifest.json") {

        event.respondWith(networkFirst(request));

        return;
    }

    // Recursos estaticos.
    if (STATIC_RESOURCES.includes(url.pathname)) {

        event.respondWith(cacheFirst(request));
    }
});

// ===================================
// Notificaciones Web Push
// ===================================

self.addEventListener("push", event => {

    const message = event.data
        ? event.data.text()
        : "Consulta las alertas de inventario.";

    event.waitUntil(

        self.registration.showNotification(
            "StockFlow | Alerta de inventario",
            {
                body: message,
                icon: "/icons/icon-192.png",
                badge: "/icons/icon-192.png",
                data: {
                    url: "/"
                }
            }
        )
    );
});

// Abrir StockFlow al pulsar la notificacion.

self.addEventListener("notificationclick", event => {

    event.notification.close();

    event.waitUntil((async () => {

        const windows = await self.clients.matchAll({
            type: "window",
            includeUncontrolled: true
        });

        const target =
            new URL("/", self.location.origin).href;

        for (const client of windows) {

            if (client.url === target) {

                return client.focus();
            }
        }

        return self.clients.openWindow(target);

    })());
});

