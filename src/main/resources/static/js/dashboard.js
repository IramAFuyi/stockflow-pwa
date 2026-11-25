
const search = document.getElementById("search");
const categoryFilter = document.getElementById("category-filter");
const stockFilter = document.getElementById("stock-filter");

const rows = document.querySelectorAll("#product-table tr");
const resultCount = document.getElementById("result-count");
const emptyMessage = document.getElementById("empty-message");

const connectionStatus =
    document.getElementById("connection-status");

const sidebar = document.querySelector(".sidebar");
const menuToggle = document.getElementById("menu-toggle");

// Normalizar texto para buscar sin distinguir acentos.
function normalizeText(text) {
    return text
        .toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "");
}

// Filtrar productos.
function filterProducts() {
    const query = normalizeText(search.value.trim());
    const category = categoryFilter.value;
    const stockState = stockFilter.value;

    let visible = 0;

    rows.forEach(row => {
        const text = normalizeText(row.textContent);
        const stock = Number(row.dataset.stock);
        const min = Number(row.dataset.min);

        const matchesSearch = text.includes(query);

        const matchesCategory =
            category === "all" ||
            row.dataset.category === category;

        let matchesStock = true;

        switch (stockState) {
            case "available":
                matchesStock = stock > min;
                break;

            case "low":
                matchesStock = stock > 0 && stock <= min;
                break;

            case "empty":
                matchesStock = stock === 0;
                break;
        }

        const show =
            matchesSearch &&
            matchesCategory &&
            matchesStock;

        row.hidden = !show;

        if (show) {
            visible++;
        }
    });

    resultCount.textContent =
        `${visible} de ${rows.length} productos encontrados`;

    emptyMessage.hidden = visible !== 0;
}

// Estado de la red.
function updateConnectionStatus() {
    const online = navigator.onLine;

    connectionStatus.textContent = online
        ? "● Conectado a la red"
        : "● Sin conexión";

    connectionStatus.classList.toggle("online", online);
    connectionStatus.classList.toggle("offline", !online);
}

// Menu lateral en dispositivos moviles.
function closeMenu() {
    sidebar.classList.remove("open");
    menuToggle.setAttribute("aria-expanded", "false");
}

menuToggle.addEventListener("click", () => {
    const opened = sidebar.classList.toggle("open");

    menuToggle.setAttribute(
        "aria-expanded",
        String(opened)
    );
});

document.addEventListener("click", event => {
    if (!sidebar.contains(event.target) &&
        !menuToggle.contains(event.target)) {
        closeMenu();
    }
});

sidebar.querySelectorAll("a").forEach(link => {
    link.addEventListener("click", closeMenu);
});

document.addEventListener("keydown", event => {
    if (event.key === "Escape") {
        closeMenu();
    }
});

// Eventos de los filtros.
search.addEventListener("input", filterProducts);

categoryFilter.addEventListener(
    "change", filterProducts
);

stockFilter.addEventListener(
    "change", filterProducts
);

window.addEventListener("online", updateConnectionStatus);
window.addEventListener("offline", updateConnectionStatus);

// Inicializacion.
filterProducts();
updateConnectionStatus();
