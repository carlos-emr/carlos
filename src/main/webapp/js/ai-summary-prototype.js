/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
"use strict";
(() => {
    const nav = document.querySelector(".view-tabs");
    const tabs = [...nav.querySelectorAll("a:not(.evidence-shortcut)")];
    const panels = [...document.querySelectorAll(".view-panel")];
    nav.setAttribute("role", "tablist");
    tabs.forEach((tab, index) => {
        tab.id = "tab-" + panels[index].id;
        tab.setAttribute("role", "tab");
        tab.setAttribute("aria-controls", panels[index].id);
        panels[index].setAttribute("role", "tabpanel");
        panels[index].setAttribute("aria-labelledby", tab.id);
        panels[index].tabIndex = 0;
        tab.addEventListener("click", () => show(panels[index].id));
        tab.addEventListener("keydown", event => {
            let next;
            if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
            if (event.key === "ArrowLeft") next = (index + tabs.length - 1) % tabs.length;
            if (event.key === "Home") next = 0;
            if (event.key === "End") next = tabs.length - 1;
            if (next !== undefined) {
                event.preventDefault();
                tabs[next].focus();
                tabs[next].click();
            }
        });
    });
    function show(id) {
        panels.forEach(panel => { panel.hidden = panel.id !== id; });
        tabs.forEach(tab => {
            const active = tab.hash === "#" + id;
            tab.setAttribute("aria-selected", String(active));
            tab.tabIndex = active ? 0 : -1;
        });
    }
    function followHash() {
        const id = location.hash.slice(1);
        if (panels.some(panel => panel.id === id)) show(id);
        const source = document.getElementById(id);
        if (source && source.classList.contains("source")) {
            source.open = true;
            source.querySelector("p").focus({preventScroll: true});
            source.scrollIntoView({block: "nearest"});
        }
    }
    document.querySelectorAll(".citation").forEach(link => link.addEventListener("click", () => {
        const source = document.getElementById(link.hash.slice(1));
        if (source) {
            source.open = true;
            source.querySelector("p").focus({preventScroll: true});
        }
    }));
    show("summary");
    followHash();
    window.addEventListener("hashchange", followHash);
})();
