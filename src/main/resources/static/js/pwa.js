
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