/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
"use strict";
// Preserve a normal CSRF-protected form POST while preventing accidental duplicate inference.
document.getElementById("document-summary-form")?.addEventListener("submit", function () {
    const button = this.querySelector("button[type=submit]");
    button.disabled = true;
    button.textContent = this.dataset.pending;
    this.setAttribute("aria-busy", "true");
});
