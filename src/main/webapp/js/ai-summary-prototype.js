/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
"use strict";
(() => {
    const workspace = document.getElementById("workspace");
    const nav = document.querySelector(".view-tabs");
    const tabs = [...nav.querySelectorAll("a")];
    const panels = [...document.querySelectorAll(".view-panel")];
    const evidence = document.getElementById("evidence");
    const sources = [...evidence.querySelectorAll(".source")];
    const claims = [...document.querySelectorAll(".claim")];
    const picker = document.getElementById("source-picker");
    const splitter = document.getElementById("pane-splitter");
    const showButton = document.getElementById("show-evidence");
    const previousClaim = document.getElementById("previous-claim");
    const nextClaim = document.getElementById("next-claim");
    let selectedSources = [];
    let sourceIndex = 0;
    let activeClaim = -1;
    let evidenceWidth = 28;

    document.body.classList.add("enhanced");
    document.querySelectorAll(".js-control").forEach(control => { control.hidden = false; });
    showButton.hidden = true;
    sources.forEach(source => { source.hidden = true; });
    document.getElementById("empty-evidence").hidden = false;
    document.getElementById("evidence-content").hidden = true;
    nav.setAttribute("role", "tablist");
    tabs.forEach((tab, index) => {
        const panel = document.getElementById(tab.hash.slice(1));
        tab.id = "tab-" + panel.id;
        tab.setAttribute("role", "tab");
        tab.setAttribute("aria-controls", panel.id);
        panel.setAttribute("role", "tabpanel");
        panel.setAttribute("aria-labelledby", tab.id);
        panel.tabIndex = 0;
        tab.addEventListener("click", () => show(panel.id));
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

    function setEvidenceVisible(visible) {
        workspace.classList.toggle("evidence-closed", !visible);
        evidence.hidden = !visible;
        splitter.hidden = !visible;
        showButton.hidden = visible;
        showButton.setAttribute("aria-expanded", String(visible));
    }

    function showSource(index, focus = false) {
        sourceIndex = Math.max(0, Math.min(index, selectedSources.length - 1));
        const selected = selectedSources[sourceIndex];
        if (!selected) return;
        sources.forEach(source => { source.hidden = source !== selected; });
        selected.open = true;
        picker.value = String(sourceIndex);
        document.getElementById("source-position").textContent =
            "Source " + (sourceIndex + 1) + " of " + selectedSources.length;
        document.getElementById("previous-source").disabled = sourceIndex === 0;
        document.getElementById("next-source").disabled = sourceIndex === selectedSources.length - 1;
        if (focus) {
            selected.querySelector("p").focus({preventScroll: true});
            if (window.innerWidth <= 1100) evidence.scrollIntoView({block: "start"});
        }
    }

    function selectSources(ids, statement, focus = true) {
        selectedSources = [...new Set(ids)].map(id => document.getElementById(id))
            .filter(source => sources.includes(source));
        if (!selectedSources.length) return;
        setEvidenceVisible(true);
        document.getElementById("empty-evidence").hidden = true;
        document.getElementById("evidence-content").hidden = false;
        const selectedStatement = document.getElementById("selected-statement");
        selectedStatement.hidden = !statement;
        selectedStatement.querySelector("p").textContent = statement || "";
        document.getElementById("evidence-subtitle").textContent =
            selectedSources.length + (selectedSources.length === 1 ? " linked source" : " linked sources");
        picker.replaceChildren();
        selectedSources.forEach((source, index) => {
            const option = document.createElement("option");
            option.value = String(index);
            option.textContent = source.querySelector("summary strong").textContent;
            picker.append(option);
        });
        document.getElementById("source-switcher").hidden = false;
        previousClaim.disabled = activeClaim <= 0;
        nextClaim.disabled = activeClaim < 0 || activeClaim >= claims.length - 1;
        showSource(0, focus);
    }

    function selectClaim(claim, focus = true) {
        activeClaim = claims.indexOf(claim);
        claims.forEach(item => {
            item.classList.toggle("active", item === claim);
            if (item === claim) item.setAttribute("aria-current", "true");
            else item.removeAttribute("aria-current");
        });
        const links = [...claim.closest(".claim-row").querySelectorAll(".citations a")];
        selectSources(links.map(link => link.hash.slice(1)), claim.querySelector(".claim-text").textContent, focus);
    }

    claims.forEach(claim => claim.addEventListener("click", event => {
        event.preventDefault();
        selectClaim(claim);
        history.replaceState(null, "", claim.hash);
    }));
    document.querySelectorAll(".citation").forEach(link => link.addEventListener("click", event => {
        event.preventDefault();
        activeClaim = -1;
        claims.forEach(claim => { claim.classList.remove("active"); claim.removeAttribute("aria-current"); });
        selectSources([link.hash.slice(1)], "");
        history.replaceState(null, "", link.hash);
    }));
    picker.addEventListener("change", () => showSource(Number(picker.value)));
    document.getElementById("previous-source").addEventListener("click", () => showSource(sourceIndex - 1));
    document.getElementById("next-source").addEventListener("click", () => showSource(sourceIndex + 1));
    previousClaim.addEventListener("click", () => {
        if (activeClaim > 0) { show("summary"); selectClaim(claims[activeClaim - 1], false); }
    });
    nextClaim.addEventListener("click", () => {
        if (activeClaim >= 0 && activeClaim < claims.length - 1) {
            show("summary");
            selectClaim(claims[activeClaim + 1], false);
        }
    });
    document.getElementById("hide-evidence").addEventListener("click", () => {
        setEvidenceVisible(false);
        showButton.focus();
    });
    showButton.addEventListener("click", () => {
        setEvidenceVisible(true);
        document.getElementById("hide-evidence").focus();
    });

    function setWidth(percent) {
        evidenceWidth = Math.max(24, Math.min(55, percent));
        workspace.style.setProperty("--evidence-width", evidenceWidth + "%");
        splitter.setAttribute("aria-valuenow", String(Math.round(evidenceWidth)));
        splitter.setAttribute("aria-valuetext", Math.round(evidenceWidth) + "% source evidence");
    }
    splitter.addEventListener("pointerdown", event => {
        if (event.button !== 0) return;
        splitter.setPointerCapture(event.pointerId);
        document.body.classList.add("resizing-evidence");
        event.preventDefault();
    });
    splitter.addEventListener("pointermove", event => {
        if (!splitter.hasPointerCapture(event.pointerId)) return;
        const rect = workspace.getBoundingClientRect();
        setWidth((rect.right - event.clientX) / rect.width * 100);
    });
    function finishResize(event) {
        if (splitter.hasPointerCapture(event.pointerId)) splitter.releasePointerCapture(event.pointerId);
        document.body.classList.remove("resizing-evidence");
    }
    splitter.addEventListener("pointerup", finishResize);
    splitter.addEventListener("pointercancel", finishResize);
    splitter.addEventListener("lostpointercapture", () => document.body.classList.remove("resizing-evidence"));
    splitter.addEventListener("dblclick", () => setWidth(28));
    splitter.addEventListener("keydown", event => {
        let width;
        if (event.key === "ArrowLeft") width = evidenceWidth + 5;
        if (event.key === "ArrowRight") width = evidenceWidth - 5;
        if (event.key === "Home") width = 55;
        if (event.key === "End") width = 24;
        if (width !== undefined) { event.preventDefault(); setWidth(width); }
    });

    function measureHeader() {
        workspace.style.setProperty("--header-height", workspace.offsetTop + "px");
    }
    const observer = new ResizeObserver(measureHeader);
    document.querySelectorAll(".app-header, .synthetic-banner, .patient-header")
        .forEach(header => observer.observe(header));
    window.addEventListener("resize", measureHeader);

    function followHash() {
        const id = location.hash.slice(1);
        if (panels.some(panel => panel.id === id)) show(id);
        else if (sources.some(source => source.id === id)) selectSources([id], "");
    }
    show("summary");
    setWidth(28);
    measureHeader();
    followHash();
    window.addEventListener("hashchange", followHash);
})();
