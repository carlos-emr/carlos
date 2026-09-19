/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// CSRFGuard supplies the XHR header; callers send this token in the form body only.
async function paymentTypeCsrfToken() {
    try {
        if (window.csrfTokenReady) await window.csrfTokenReady;
        const input = document.querySelector("input[name='CSRF-TOKEN']");
        if (!input || !input.value) throw new Error("Security token unavailable");
        return input.value;
    } catch (error) {
        alert("Security token unavailable. Reload and try again.");
        return null;
    }
}
