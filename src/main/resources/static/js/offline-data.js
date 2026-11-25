
"use strict";

const StockFlowOffline = (() => {

    async function fetchData(url) {

        const response = await stockflowFetch(url, {
            cache: "no-store"
        });

        if (!response.ok) {
            throw new Error(
                `Error al consultar ${url}`
            );
        }

        const data = await response.json();

        if (!Array.isArray(data)) {
            throw new Error("Respuesta invalida");
        }

        return data;
    }

    async function refreshCatalogs() {

        try {

            const [products, departments] =
                await Promise.all([
                    fetchData("/api/products"),
                    fetchData("/api/departments")
                ]);

            await StockFlowDB.replaceAll(
                "products", products
            );

            await StockFlowDB.replaceAll(
                "departments", departments
            );

            await StockFlowDB.put("metadata", {

                key: "lastSync",
                value: new Date().toISOString()
            });

            console.log(
                "[StockFlow] Catalogos actualizados",
                {
                    products: products.length,
                    departments: departments.length
                }
            );

            return true;

        } catch (error) {

            console.warn(
                "[StockFlow] Se conservaran los datos locales:",
                error.message
            );

            return false;
        }
    }

    async function getProducts() {

        return StockFlowDB.getAll("products");
    }

    async function getDepartments() {

        return StockFlowDB.getAll("departments");
    }

    async function getLastSync() {

        const metadata = await StockFlowDB.get(
            "metadata", "lastSync"
        );

        return metadata?.value ?? null;
    }

    return {
        refreshCatalogs,
        getProducts,
        getDepartments,
        getLastSync
    };

})();

window.StockFlowOffline = StockFlowOffline;
