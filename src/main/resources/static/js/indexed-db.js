
"use strict";

const StockFlowDB = (() => {

    const DB_NAME = "stockflow-db";
    const DB_VERSION = 1;

    let dbPromise = null;

    function open() {

        if (dbPromise) {
            return dbPromise;
        }

        dbPromise = new Promise((resolve, reject) => {

            const request = indexedDB.open(
                DB_NAME, DB_VERSION
            );

            request.onupgradeneeded = event => {

                const db = event.target.result;

                if (!db.objectStoreNames.contains("products")) {

                    db.createObjectStore("products", {
                        keyPath: "id"
                    });
                }

                if (!db.objectStoreNames.contains("departments")) {

                    db.createObjectStore("departments", {
                        keyPath: "id"
                    });
                }

                if (!db.objectStoreNames.contains("pendingMovements")) {

                    db.createObjectStore("pendingMovements", {
                        keyPath: "operationUuid"
                    });
                }

                if (!db.objectStoreNames.contains("metadata")) {

                    db.createObjectStore("metadata", {
                        keyPath: "key"
                    });
                }
            };

            request.onsuccess = () => {

                const db = request.result;

                db.onversionchange = () => {
                    db.close();
                    dbPromise = null;
                };

                resolve(db);
            };

            request.onerror = () => {

                dbPromise = null;
                reject(request.error);
            };
        });

        return dbPromise;
    }

    async function execute(storeName, mode, action) {

        const db = await open();

        return new Promise((resolve, reject) => {

            const transaction = db.transaction(
                storeName, mode
            );

            const store = transaction.objectStore(storeName);

            const request = action(store);

            transaction.oncomplete = () => {
                resolve(request?.result);
            };

            transaction.onerror = () => {
                reject(transaction.error);
            };

            transaction.onabort = () => {
                reject(transaction.error ||
                    new Error("Transaccion cancelada"));
            };
        });
    }

    async function put(storeName, data) {

        return execute(
            storeName,
            "readwrite",
            store => store.put(data)
        );
    }

    async function get(storeName, key) {

        return execute(
            storeName,
            "readonly",
            store => store.get(key)
        );
    }

    async function getAll(storeName) {

        return execute(
            storeName,
            "readonly",
            store => store.getAll()
        );
    }

    async function remove(storeName, key) {

        return execute(
            storeName,
            "readwrite",
            store => store.delete(key)
        );
    }

    async function replaceAll(storeName, items) {

        const db = await open();

        return new Promise((resolve, reject) => {

            const transaction = db.transaction(
                storeName,
                "readwrite"
            );

            const store = transaction.objectStore(storeName);

            store.clear();

            items.forEach(item => store.put(item));

            transaction.oncomplete = resolve;

            transaction.onerror = () => {
                reject(transaction.error);
            };

            transaction.onabort = () => {
                reject(transaction.error ||
                    new Error("Transaccion cancelada"));
            };
        });
    }

    async function count(storeName) {

        return execute(
            storeName,
            "readonly",
            store => store.count()
        );
    }

    return {
        open,
        put,
        get,
        getAll,
        remove,
        replaceAll,
        count
    };

})();

window.StockFlowDB = StockFlowDB;
