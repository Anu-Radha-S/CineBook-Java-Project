// CineBook website. Every page loads this file; <body data-page="..."> decides what runs.

// ---------- helpers shared by all pages ----------

async function getJson(url) {
    const res = await fetch(url);
    const json = await res.json();
    if (!res.ok) {
        throw new Error(json.error);
    }
    return json;
}

async function postForm(url, data) {
    const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams(data)
    });
    const json = await res.json();
    if (!res.ok) {
        throw new Error(json.error);
    }
    return json;
}

function rs(amount) {
    return "Rs. " + Number(amount).toFixed(2);
}

// 169 -> "2h 49m"
function duration(minutes) {
    return Math.floor(minutes / 60) + "h " + (minutes % 60) + "m";
}

// "Sci-Fi, Drama  /  2h 49m  /  UA"
function detailsLine(show) {
    return show.genre + "  /  " + duration(show.duration) + "  /  " + show.certificate;
}

function param(name) {
    return new URLSearchParams(window.location.search).get(name);
}

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
        node.className = className;
    }
    if (text !== undefined) {
        node.textContent = text;
    }
    return node;
}

function showNotice(id, text, isError) {
    const box = document.getElementById(id);
    box.textContent = text;
    box.className = "notice" + (isError ? " error" : "");
    box.hidden = false;
}

// A poster shows the movie's image from web/posters. If there is no image (or it fails to load)
// it falls back to a solid colour block (colour picked from the show id) with the title on it.
function fillPoster(poster, show, subText) {
    poster.classList.add("poster-" + (show.id % 4));
    poster.classList.remove("has-image");
    poster.innerHTML = "";
    poster.append(el("span", "poster-title", show.movie));
    poster.append(el("span", "poster-sub", subText));

    if (show.poster) {
        const img = document.createElement("img");
        img.alt = show.movie + " poster";
        img.onload = () => poster.classList.add("has-image");
        img.onerror = () => img.remove();
        img.src = "posters/" + encodeURIComponent(show.poster);
        poster.prepend(img);
    }
}

function highlightNav() {
    const page = document.body.dataset.page;
    document.querySelectorAll(".nav a").forEach(a => {
        if (a.dataset.nav === page) {
            a.classList.add("active");
        }
    });
}

// ---------- home page ----------

async function initHome() {
    const shows = await getJson("/api/shows");
    if (shows.length === 0) {
        return;
    }

    const featured = shows[0];
    document.getElementById("hero-title").textContent = featured.movie;
    document.getElementById("hero-meta").textContent =
        detailsLine(featured) + "  /  " + featured.time + "  /  " + featured.free + " of 40 seats free";
    document.getElementById("hero-book").href = "book.html?show=" + featured.id;
    fillPoster(document.getElementById("hero-poster"), featured, featured.time);

    const grid = document.getElementById("show-grid");
    for (const s of shows) {
        const card = el("a", "show-card");
        card.href = "book.html?show=" + s.id;

        const poster = el("div", "poster");
        fillPoster(poster, s, s.certificate + "  /  " + duration(s.duration));

        const info = el("div", "show-info");
        info.append(el("strong", "", s.movie), el("span", "", s.time));

        card.append(poster, info, el("p", "show-genre", s.genre), el("p", "show-free", s.free + " seats free"));
        grid.append(card);
    }
}

// ---------- booking page ----------

const ROWS_WITH_GAP_AFTER = ["B", "D"];   // space between Regular, Premium and VIP rows
const AISLE_BEFORE_COLUMN = 5;            // walkway between seat 4 and seat 5

let showId = null;
let rowPrices = {};    // row letter -> price, e.g. { A: 150, ... }
let rowTypes = {};     // row letter -> "Regular" / "Premium" / "VIP"
let picked = [];       // seat codes the user chose, e.g. ["C3", "C4"]

async function initBook() {
    showId = param("show");
    if (!showId) {
        window.location.href = "index.html";
        return;
    }
    document.getElementById("book-btn").onclick = bookSeats;
    try {
        await loadSeatMap();
    } catch (err) {
        showNotice("book-message", err.message, true);
    }
}

async function loadSeatMap() {
    const data = await getJson("/api/seats?show=" + showId);
    document.getElementById("book-title").textContent = data.movie;
    document.getElementById("book-time").textContent = "Today  /  " + data.time + "  /  Screen 1";
    document.getElementById("book-details").textContent = detailsLine(data);
    document.title = data.movie + " | CineBook";
    fillPoster(document.getElementById("book-poster"), data, data.time);

    const prices = document.getElementById("price-list");
    prices.innerHTML = "";
    const listed = [];

    const map = document.getElementById("seat-map");
    map.innerHTML = "";

    // column numbers along the top
    const header = el("div", "seat-row");
    header.append(el("span", "row-label", ""));
    for (let c = 1; c <= data.rows[0].seats.length; c++) {
        header.append(el("span", "col-label" + (c === AISLE_BEFORE_COLUMN ? " aisle" : ""), String(c)));
    }
    map.append(header);

    for (const row of data.rows) {
        rowPrices[row.row] = row.price;
        rowTypes[row.row] = row.type;

        if (!listed.includes(row.type)) {
            listed.push(row.type);
            const rowsOfType = data.rows.filter(r => r.type === row.type).map(r => r.row);
            const li = el("li");
            li.append(el("span", "", row.type + "  (Row " + rowsOfType.join(", ") + ")"),
                      el("strong", "", rs(row.price)));
            prices.append(li);
        }

        const line = el("div", "seat-row" + (ROWS_WITH_GAP_AFTER.includes(row.row) ? " gap-after" : ""));
        line.append(el("span", "row-label", row.row));

        for (let c = 0; c < row.seats.length; c++) {
            const code = row.row + (c + 1);
            const seat = el("button", "seat", String(c + 1));
            if (c + 1 === AISLE_BEFORE_COLUMN) {
                seat.classList.add("aisle");
            }
            seat.title = code + "  " + row.type + "  " + rs(row.price);
            if (row.seats[c] === "X") {
                seat.classList.add("booked");
                seat.disabled = true;
            } else {
                if (picked.includes(code)) {
                    seat.classList.add("selected");
                }
                seat.onclick = () => toggleSeat(code);
            }
            seat.dataset.code = code;
            line.append(seat);
        }
        map.append(line);
    }
    renderPicked();
}

function toggleSeat(code) {
    if (picked.includes(code)) {
        picked = picked.filter(s => s !== code);
    } else {
        picked.push(code);
    }
    const seat = document.querySelector('.seat[data-code="' + code + '"]');
    seat.classList.toggle("selected", picked.includes(code));
    renderPicked();
}

function renderPicked() {
    // keep the list in seat order: A1, A2, ... E8
    picked.sort((a, b) => a[0].localeCompare(b[0]) || Number(a.slice(1)) - Number(b.slice(1)));

    const list = document.getElementById("picked-list");
    list.innerHTML = "";
    let total = 0;

    if (picked.length === 0) {
        list.append(el("li", "empty", "Click a seat on the map to add it."));
    }
    for (const code of picked) {
        total += rowPrices[code[0]];
        const li = el("li");
        const name = el("span");
        name.append(el("span", "seat-name", code), el("span", "muted", rowTypes[code[0]]));
        const right = el("span");
        const remove = el("button", "remove", "Remove");
        remove.onclick = () => toggleSeat(code);
        right.append(el("span", "", rs(rowPrices[code[0]])), remove);
        li.append(name, right);
        list.append(li);
    }

    document.getElementById("total").textContent = rs(total);
    document.getElementById("book-btn").disabled = picked.length === 0;
}

async function bookSeats() {
    const button = document.getElementById("book-btn");
    button.disabled = true;
    try {
        const b = await postForm("/api/book", {
            show: showId,
            seats: picked.join(","),
            coupon: document.getElementById("coupon").value
        });
        let next = "ticket.html?id=" + encodeURIComponent(b.bookingId) + "&new=1";
        if (b.note) {
            next += "&note=" + encodeURIComponent(b.note);
        }
        window.location.href = next;
    } catch (err) {
        showNotice("book-message", "Booking failed. " + err.message, true);
        picked = [];
        await loadSeatMap();
    }
}

// ---------- ticket page ----------

async function initTicket() {
    const id = (param("id") || "").toUpperCase();
    try {
        const all = await getJson("/api/bookings");
        const b = all.find(x => x.bookingId === id);
        if (!b) {
            throw new Error("No booking found with ID " + id + ".");
        }

        if (param("new")) {
            document.getElementById("ticket-eyebrow").textContent = "Booking confirmed";
            document.getElementById("ticket-heading").textContent = "Enjoy the show";
        }
        if (param("note")) {
            showNotice("ticket-message", param("note"), false);
        }

        document.getElementById("t-movie").textContent = b.movie;
        document.getElementById("t-time").textContent = "Today, " + b.time;
        document.getElementById("t-id").textContent = b.bookingId;
        document.getElementById("t-seats").textContent = b.seats;
        document.getElementById("t-total").textContent =
            rs(b.amount) + (b.couponApplied ? " (10% off)" : "");
        const status = document.getElementById("t-status");
        status.textContent = b.status === "BOOKED" ? "Booked" : "Cancelled";
        status.className = b.status === "BOOKED" ? "" : "status-cancelled";
        document.getElementById("t-code").textContent = b.bookingId;
        drawBarcode(b.bookingId);
        document.getElementById("ticket").hidden = false;

        if (b.status === "BOOKED") {
            const cancel = document.getElementById("ticket-cancel");
            cancel.href = "cancel.html?id=" + encodeURIComponent(b.bookingId);
            cancel.hidden = false;
        }
    } catch (err) {
        showNotice("ticket-message", err.message, true);
    }
}

// Draws bars whose widths come from the characters of the booking ID (decoration only).
function drawBarcode(text) {
    const box = document.getElementById("barcode");
    box.innerHTML = "";
    for (let i = 0; i < 26; i++) {
        const code = text.charCodeAt(i % text.length) + i * 7;
        const bar = el("i");
        bar.style.width = (1 + (code % 4)) + "px";
        if (code % 5 === 0) {
            bar.style.marginRight = "3px";
        }
        box.append(bar);
    }
}

// ---------- bookings page ----------

async function initBookings() {
    const body = document.getElementById("bookings");
    const list = await getJson("/api/bookings");
    if (list.length === 0) {
        const tr = el("tr");
        const td = el("td", "muted", "No bookings yet. Pick a show on the home page to make one.");
        td.colSpan = 6;
        tr.append(td);
        body.append(tr);
        return;
    }
    for (const b of list.reverse()) {
        const tr = el("tr");
        tr.append(
            el("td", "id", b.bookingId),
            el("td", "", b.movie + ", " + b.time),
            el("td", "", b.seats),
            el("td", "", rs(b.amount) + (b.couponApplied ? " (CINE10)" : ""))
        );

        const statusCell = el("td");
        statusCell.append(el("span", "tag " + b.status.toLowerCase(),
            b.status === "BOOKED" ? "BOOKED" : "CANCELLED"));
        tr.append(statusCell);

        const actions = el("td", "row-actions");
        const view = el("a", "btn btn-outline btn-small", "Ticket");
        view.href = "ticket.html?id=" + encodeURIComponent(b.bookingId);
        actions.append(view);
        if (b.status === "BOOKED") {
            const cancel = el("a", "btn btn-outline btn-danger btn-small", "Cancel");
            cancel.href = "cancel.html?id=" + encodeURIComponent(b.bookingId);
            actions.append(cancel);
        }
        tr.append(actions);
        body.append(tr);
    }
}

// ---------- cancel page ----------

function initCancel() {
    const input = document.getElementById("cancel-id");
    input.value = param("id") || "";
    document.getElementById("cancel-btn").onclick = cancelBooking;
}

async function cancelBooking() {
    const id = document.getElementById("cancel-id").value.trim();
    document.getElementById("refund").hidden = true;
    document.getElementById("cancel-message").hidden = true;
    if (!id) {
        showNotice("cancel-message", "Enter a booking ID first.", true);
        return;
    }
    try {
        const r = await postForm("/api/cancel", { id: id });
        document.getElementById("refund-amount").textContent = rs(r.refund);
        document.getElementById("refund-detail").textContent =
            "Booking " + r.bookingId + " is cancelled. Seats " + r.seats
            + " are free again. You paid " + rs(r.amount) + " and get 80% back.";
        document.getElementById("refund").hidden = false;
    } catch (err) {
        showNotice("cancel-message", err.message, true);
    }
}

// ---------- reports page ----------

async function initReports() {
    const data = await getJson("/api/report");

    let revenue = 0;
    let sold = 0;
    let active = 0;
    let cancelled = 0;
    const body = document.getElementById("report-shows");
    for (const r of data.shows) {
        revenue += r.revenue;
        sold += r.seatsBooked;
        active += r.activeBookings;
        cancelled += r.cancelledBookings;

        const total = r.seatsBooked + r.seatsFree;
        const percent = total === 0 ? 0 : Math.round(r.seatsBooked * 100 / total);

        const tr = el("tr");
        const show = el("td");
        show.append(el("strong", "", r.movie), el("span", "muted", "  " + r.time));

        const occupancy = el("td");
        const bar = el("div", "bar");
        const fill = el("div", "bar-fill");
        fill.style.width = percent + "%";
        bar.append(fill);
        occupancy.append(bar, el("span", "bar-text", percent + "%"));

        tr.append(show,
            el("td", "", r.seatsBooked + " / " + total),
            occupancy,
            el("td", "", String(r.activeBookings)),
            el("td", "", String(r.cancelledBookings)),
            el("td", "num", rs(r.revenue)));
        body.append(tr);
    }

    const stats = document.getElementById("stats");
    const figures = [
        ["Total revenue", rs(revenue)],
        ["Seats sold", String(sold)],
        ["Active bookings", String(active)],
        ["Cancelled", String(cancelled)]
    ];
    for (const [label, value] of figures) {
        const box = el("div", "stat");
        box.append(el("p", "stat-label", label), el("p", "stat-value", value));
        stats.append(box);
    }

    const types = document.getElementById("report-types");
    for (const t of data.seatTypes) {
        const tr = el("tr");
        tr.append(el("td", "", t.type), el("td", "", rs(t.price)), el("td", "num", String(t.sold)));
        types.append(tr);
    }
}

// ---------- start ----------

highlightNav();
const pages = {
    home: initHome,
    book: initBook,
    ticket: initTicket,
    bookings: initBookings,
    cancel: initCancel,
    reports: initReports
};
pages[document.body.dataset.page]();
