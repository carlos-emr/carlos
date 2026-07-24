document.addEventListener("click", (event) => {
  if (!(event.target instanceof Element)) {
    return;
  }

  const revealButton = event.target.closest("[data-reveal-url][data-reveal-target]");
  if (!(revealButton instanceof HTMLButtonElement)) {
    return;
  }

  const revealUrl = revealButton.dataset.revealUrl;
  const targetId = revealButton.dataset.revealTarget;
  const csrfToken = revealButton.dataset.csrfToken;
  const target = targetId ? document.getElementById(targetId) : null;
  if (!revealUrl || !csrfToken || !(target instanceof HTMLElement)) {
    return;
  }

  revealButton.disabled = true;
  revealButton.textContent = revealButton.dataset.revealingLabel || "Revealing...";
  void fetch(revealUrl, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: `csrf_token=${encodeURIComponent(csrfToken)}`,
  })
    .then((response) => {
      if (!response.ok) {
        throw new Error("reveal failed");
      }
      return response.json();
    })
    .then((payload) => {
      if (
        typeof payload !== "object"
        || payload === null
        || typeof payload.passphrase !== "string"
      ) {
        throw new Error("invalid reveal response");
      }
      target.textContent = payload.passphrase;
      const copyButton = target.parentElement?.querySelector("[data-copy-target]");
      if (copyButton instanceof HTMLButtonElement) {
        copyButton.hidden = false;
      }
      revealButton.hidden = true;
      target.focus();
    })
    .catch(() => {
      revealButton.textContent = (
        revealButton.dataset.revealFailedLabel || "Password could not be revealed."
      );
      window.setTimeout(() => {
        revealButton.textContent = revealButton.dataset.revealLabel || "Reveal";
        revealButton.disabled = false;
      }, 2500);
    });
});

document.addEventListener("click", (event) => {
  if (!(event.target instanceof Element)) {
    return;
  }

  const copyButton = event.target.closest("[data-copy-target]");
  if (!(copyButton instanceof HTMLButtonElement)) {
    return;
  }

  const targetId = copyButton.dataset.copyTarget;
  const target = targetId ? document.getElementById(targetId) : null;
  if (!(target instanceof HTMLElement)) {
    return;
  }

  const copyValue = (
    target instanceof HTMLInputElement ? target.value : target.textContent || ""
  ).trim();
  if (target instanceof HTMLInputElement) {
    target.select();
    target.setSelectionRange(0, target.value.length);
  }
  if (!navigator.clipboard) {
    return;
  }

  void navigator.clipboard.writeText(copyValue).then(() => {
    const originalText = copyButton.textContent || copyButton.dataset.copyLabel || "Copy";
    copyButton.textContent = copyButton.dataset.copiedLabel || "Copied";
    window.setTimeout(() => {
      copyButton.textContent = originalText;
    }, 1500);
  });
});

const resetTokenForm = document.querySelector("[data-reset-token-form]");
const resetTokenInput = document.querySelector("[data-reset-token]");
const resetTokenError = document.querySelector("[data-reset-token-error]");

if (
  resetTokenForm instanceof HTMLFormElement
  && resetTokenInput instanceof HTMLInputElement
) {
  const fragmentValues = new URLSearchParams(window.location.hash.slice(1));
  const fragmentResetToken = fragmentValues.get("token") || "";
  const resetToken = fragmentResetToken || resetTokenInput.value;
  resetTokenInput.value = resetToken;
  if (fragmentResetToken) {
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
  }
  if (!resetToken && resetTokenError instanceof HTMLElement) {
    resetTokenError.hidden = false;
  }
  resetTokenForm.addEventListener("submit", (event) => {
    if (!resetTokenInput.value) {
      event.preventDefault();
      if (resetTokenError instanceof HTMLElement) {
        resetTokenError.hidden = false;
        resetTokenError.focus();
      }
    }
  });
}

const portalMessageModal = document.getElementById("portal-message-modal");
const portalMessageTitle = document.getElementById("portal-message-title");
const portalMessageBody = document.getElementById("portal-message-body");

function hidePortalMessageModal() {
  if (!(portalMessageModal instanceof HTMLElement)) {
    return;
  }
  portalMessageModal.hidden = true;
}

function showPortalMessageModal(title, message) {
  if (
    !(portalMessageModal instanceof HTMLElement)
    || !(portalMessageTitle instanceof HTMLElement)
    || !(portalMessageBody instanceof HTMLElement)
  ) {
    return;
  }

  portalMessageTitle.textContent = title;
  portalMessageBody.textContent = message;
  portalMessageModal.hidden = false;
  const closeButton = portalMessageModal.querySelector("[data-modal-close]");
  if (closeButton instanceof HTMLElement) {
    closeButton.focus();
  }
}

document.addEventListener("click", (event) => {
  if (!(event.target instanceof Element)) {
    return;
  }

  const modalTrigger = event.target.closest("[data-modal-title][data-modal-message]");
  if (modalTrigger instanceof HTMLElement) {
    event.preventDefault();
    showPortalMessageModal(
      modalTrigger.dataset.modalTitle || "",
      modalTrigger.dataset.modalMessage || "",
    );
    return;
  }

  const modalClose = event.target.closest("[data-modal-close]");
  if (modalClose instanceof HTMLElement) {
    event.preventDefault();
    hidePortalMessageModal();
    return;
  }

  if (event.target === portalMessageModal) {
    hidePortalMessageModal();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    hidePortalMessageModal();
  }
});
