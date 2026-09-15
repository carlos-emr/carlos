/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL-2.0-or-later.
 *
 * Staff-facing patient portal account controls. All server-provided text is assigned through
 * textContent; no portal response is interpreted as markup.
 */
(function () {
    "use strict";

    const root = document.getElementById("portal-account");
    if (!root) {
        return;
    }

    const contextPath = root.dataset.contextPath;
    const demographicNo = root.dataset.demographicNo;
    const mayManage = root.dataset.canManage === "true";
    const mayUnlock = root.dataset.canUnlock === "true";
    const status = document.getElementById("portal-account-status");
    const details = document.getElementById("portal-account-details");
    const state = document.getElementById("portal-account-state");
    const locked = document.getElementById("portal-account-locked");
    const reset = document.getElementById("portal-account-reset");
    const disabledAtLabel = document.getElementById("portal-account-disabled-at-label");
    const disabledAt = document.getElementById("portal-account-disabled-at");
    const disabledReasonLabel = document.getElementById("portal-account-disabled-reason-label");
    const disabledReasonValue = document.getElementById("portal-account-disabled-reason");
    const refresh = document.getElementById("portal-account-refresh");
    const unlock = document.getElementById("portal-account-unlock");
    const access = document.getElementById("portal-account-access");
    const disableFields = document.getElementById("portal-account-disable-fields");
    const disableReason = document.getElementById("portal-account-disable-reason");
    let account = null;

    function showStatus(message, error) {
        status.textContent = message;
        status.classList.toggle("portal-account__status--error", Boolean(error));
    }

    function setBusy(busy) {
        refresh.disabled = busy;
        if (unlock) {
            unlock.disabled = busy;
        }
        if (access) {
            access.disabled = busy;
        }
    }

    function hideAccountControls() {
        if (unlock) {
            unlock.hidden = true;
        }
        if (access) {
            access.hidden = true;
        }
        if (disableFields) {
            disableFields.hidden = true;
        }
    }

    function renderAccount(value) {
        account = value;
        const isDisabled = value.status === "disabled";
        state.textContent = value.status;
        locked.textContent = value.locked ? "Yes" : "No";
        reset.textContent = value.forcePasswordReset ? "Yes" : "No";
        disabledAt.textContent = value.disabledAt || "Not recorded";
        disabledReasonValue.textContent = value.disabledReason || "Not recorded";
        disabledAtLabel.hidden = !isDisabled;
        disabledAt.hidden = !isDisabled;
        disabledReasonLabel.hidden = !isDisabled;
        disabledReasonValue.hidden = !isDisabled;
        details.hidden = false;
        if (unlock) {
            unlock.hidden = !mayUnlock || !value.locked;
        }
        if (access) {
            access.hidden = !mayManage;
            access.textContent = isDisabled ? "Re-enable account" : "Disable account";
            access.dataset.enable = String(isDisabled);
            if (disableFields) {
                disableFields.hidden = isDisabled || !mayManage;
            }
        }
    }

    async function jsonResponse(response) {
        const contentType = response.headers.get("content-type") || "";
        if (!contentType.toLowerCase().startsWith("application/json")) {
            throw new Error("CARLOS returned an unreadable response.");
        }
        return response.json();
    }

    async function loadAccount(successMessage) {
        setBusy(true);
        details.hidden = true;
        hideAccountControls();
        account = null;
        showStatus("Loading portal account…", false);
        try {
            const parameters = new URLSearchParams({demographicNo: demographicNo});
            const response = await fetch(contextPath + "/demographic/portalPanel?" + parameters, {
                method: "GET",
                credentials: "same-origin",
                headers: {"Accept": "application/json"}
            });
            const payload = await jsonResponse(response);
            if (!response.ok) {
                throw new Error(payload.message || "The portal account could not be loaded.");
            }
            if (!payload.account) {
                throw new Error(
                    "The portal account status is unavailable. If this affects every patient, check the portal connection."
                );
            }
            renderAccount(payload.account);
            showStatus(successMessage || "Portal account loaded.", false);
        } catch (error) {
            const message = error instanceof Error ? error.message : "The portal account could not be loaded.";
            showStatus(message, true);
        } finally {
            setBusy(false);
        }
    }

    async function csrfToken() {
        const input = document.querySelector('input[name="CSRF-TOKEN"]');
        if (input && !input.value && typeof window.fetchCsrfToken === "function") {
            await window.fetchCsrfToken(contextPath);
        }
        if (!input || !input.value) {
            throw new Error("The security token could not be loaded. Refresh the page and try again.");
        }
        return input.value;
    }

    async function mutate(parameters) {
        setBusy(true);
        showStatus("Updating portal account…", false);
        try {
            const token = await csrfToken();
            const response = await fetch(contextPath + "/demographic/portalAccount", {
                method: "POST",
                credentials: "same-origin",
                headers: {
                    "Accept": "application/json",
                    "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                    "X-Requested-With": "XMLHttpRequest",
                    "CSRF-TOKEN": token
                },
                body: new URLSearchParams(Object.assign({demographicNo: demographicNo}, parameters))
            });
            const payload = await jsonResponse(response);
            if (!response.ok || !payload.ok) {
                throw new Error(payload.message || "The portal account could not be updated.");
            }
            if (disableReason && parameters.method === "access" && parameters.enabled === "false") {
                disableReason.value = "";
            }
            await loadAccount(payload.note || "Portal account updated.");
        } catch (error) {
            const message = error instanceof Error ? error.message : "The portal account could not be updated.";
            showStatus(message, true);
        } finally {
            setBusy(false);
        }
    }

    refresh.addEventListener("click", function () {
        loadAccount();
    });
    if (unlock) {
        unlock.addEventListener("click", function () {
            if (window.confirm(
                "Clear the lockout for CARLOS patient " + demographicNo
                    + " and require a password reset?"
            )) {
                mutate({method: "unlock"});
            }
        });
    }
    if (access) {
        access.addEventListener("click", function () {
            if (!account) {
                return;
            }
            const enable = access.dataset.enable === "true";
            if (enable) {
                if (window.confirm("Re-enable the portal account for CARLOS patient " + demographicNo + "?")) {
                    mutate({method: "access", enabled: "true"});
                }
                return;
            }
            const normalized = disableReason ? disableReason.value.trim() : "";
            if (!normalized || normalized.length > 64) {
                showStatus("Enter a reason between 1 and 64 characters.", true);
                if (disableReason) {
                    disableReason.focus();
                }
                return;
            }
            if (window.confirm("Disable the portal account for CARLOS patient " + demographicNo + "?")) {
                mutate({method: "access", enabled: "false", reason: normalized});
            }
        });
    }

    loadAccount();
}());
