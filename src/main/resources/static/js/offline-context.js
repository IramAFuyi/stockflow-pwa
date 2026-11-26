
"use strict";

window.StockFlowContext = (() => {

    const KEY = "stockflow-offline-context";
    const GENERATION = "stockflow-account-generation";
    const MAX_AGE = 30 * 60 * 1000;

    function remember(account) {

        let current = JSON.parse(
            localStorage.getItem(GENERATION) || "null"
        );

        if (!current || current.account !== account) {

            current = {
                account,
                id: crypto.randomUUID()
            };

            localStorage.setItem(
                GENERATION,
                JSON.stringify(current)
            );
        }

        sessionStorage.setItem(KEY, JSON.stringify({
            account,
            generation: current.id,
            expiresAt: Date.now() + MAX_AGE
        }));
    }

    function read() {

        try {

            const context = JSON.parse(
                sessionStorage.getItem(KEY) || "null"
            );

            const current = JSON.parse(
                localStorage.getItem(GENERATION) || "null"
            );

            if (!context || !current ||
                context.account !== current.account ||
                context.generation !== current.id ||
                Date.now() >= context.expiresAt) {

                return null;
            }

            return context;

        } catch {

            return null;
        }
    }

    function forget() {

        sessionStorage.removeItem(KEY);
    }

    function revoke() {

        localStorage.setItem(
            GENERATION,
            JSON.stringify({
                account: null,
                id: crypto.randomUUID()
            })
        );

        forget();
    }

    return {
        remember,
        read,
        forget,
        revoke
    };

})();

if (location.pathname === "/login") {

    StockFlowContext.forget();
}
