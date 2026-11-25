
"use strict";

(() => {

    const enableButton =
        document.getElementById("enable-push");

    const disableButton =
        document.getElementById("disable-push");

    const status =
        document.getElementById("push-status");

    if (!enableButton || !disableButton || !status) {
        return;
    }

    function supported() {

        return window.isSecureContext
            && "serviceWorker" in navigator
            && "PushManager" in window
            && "Notification" in window;
    }

    function decodePublicKey(value) {

        const padding =
            "=".repeat((4 - value.length % 4) % 4);

        const base64 = (value + padding)
            .replace(/-/g, "+")
            .replace(/_/g, "/");

        return Uint8Array.from(
            atob(base64),
            char => char.charCodeAt(0)
        );
    }

    async function updateStatus() {

        if (!supported()) {

            status.textContent =
                "Este navegador no admite Web Push.";

            enableButton.disabled = true;
            disableButton.disabled = true;

            return;
        }

        const registration =
            await navigator.serviceWorker.getRegistration();

        const subscription = registration
            ? await registration.pushManager.getSubscription()
            : null;

        if (Notification.permission === "denied") {

            status.textContent =
                "Permiso de notificaciones denegado.";

            enableButton.disabled = true;

        } else {

            status.textContent = subscription
                ? "Suscripcion local activa."
                : "Notificaciones desactivadas.";

            enableButton.disabled = false;
        }

        disableButton.disabled = !subscription;
    }

    enableButton.addEventListener("click", async () => {

        enableButton.disabled = true;

        try {

            const permission =
                await Notification.requestPermission();

            if (permission !== "granted") {

                throw new Error(
                    "No se concedio el permiso."
                );
            }

            const response = await fetch(
                "/api/push/public-key",
                { cache: "no-store" }
            );

            if (!response.ok) {

                throw new Error(
                    "Web Push no esta configurado."
                );
            }

            const { publicKey } = await response.json();

            const registration =
                await navigator.serviceWorker.getRegistration();

            if (!registration?.active) {

                throw new Error(
                    "El Service Worker aun no esta activo."
                );
            }

            let subscription =
                await registration.pushManager.getSubscription();

            if (!subscription) {

                subscription =
                    await registration.pushManager.subscribe({

                        userVisibleOnly: true,

                        applicationServerKey:
                            decodePublicKey(publicKey)
                    });
            }

            const data = subscription.toJSON();

            const saved = await fetch(
                "/api/push/subscribe",
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        endpoint: data.endpoint,
                        p256dh: data.keys.p256dh,
                        auth: data.keys.auth
                    })
                }
            );

            if (!saved.ok) {

                throw new Error(
                    "No se pudo guardar la suscripcion."
                );
            }

            await updateStatus();

            status.textContent =
                "Notificaciones activadas y registradas.";

        } catch (error) {

            await updateStatus().catch(() => { });

            status.textContent = error.message;

            enableButton.disabled =
                Notification.permission === "denied";
        }
    });

    disableButton.addEventListener("click", async () => {

        disableButton.disabled = true;

        try {

            const registration =
                await navigator.serviceWorker.getRegistration();

            const subscription =
                await registration?.pushManager.getSubscription();

            if (subscription) {

                const response = await fetch(
                    "/api/push/subscribe",
                    {
                        method: "DELETE",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            endpoint: subscription.endpoint
                        })
                    }
                );

                if (!response.ok) {

                    throw new Error(
                        "No se pudo eliminar la suscripcion."
                    );
                }

                await subscription.unsubscribe();
            }

            await updateStatus();

        } catch (error) {

            status.textContent = error.message;
            disableButton.disabled = false;
        }
    });

    window.addEventListener("load", () => {

        updateStatus().catch(error => {

            status.textContent = error.message;
        });
    });

})();
