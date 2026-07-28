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
  const originalText = copyButton.textContent || copyButton.dataset.copyLabel || "Copy";
  const copyPromise = navigator.clipboard
    ? navigator.clipboard.writeText(copyValue)
    : Promise.reject(new Error("clipboard unavailable"));
  void copyPromise
    .then(() => {
      copyButton.textContent = copyButton.dataset.copiedLabel || "Copied";
    })
    .catch(() => {
      copyButton.textContent = (
        copyButton.dataset.copyFailedLabel || "Select and copy manually"
      );
      target.focus();
      const selection = window.getSelection();
      if (!(target instanceof HTMLInputElement) && selection) {
        const range = document.createRange();
        range.selectNodeContents(target);
        selection.removeAllRanges();
        selection.addRange(range);
      }
    })
    .finally(() => {
      window.setTimeout(() => {
        copyButton.textContent = originalText;
      }, 2500);
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
let portalMessageOpener = null;
let inertBackgroundElements = [];

function setPortalBackgroundInert() {
  if (!(portalMessageModal instanceof HTMLElement)) {
    return;
  }
  inertBackgroundElements = [];
  let activeBranch = portalMessageModal;
  let parent = activeBranch.parentElement;
  while (parent instanceof HTMLElement) {
    for (const sibling of parent.children) {
      if (sibling !== activeBranch && sibling instanceof HTMLElement && !sibling.inert) {
        sibling.inert = true;
        inertBackgroundElements.push(sibling);
      }
    }
    activeBranch = parent;
    parent = parent.parentElement;
  }
}

function restorePortalBackground() {
  for (const element of inertBackgroundElements) {
    element.inert = false;
  }
  inertBackgroundElements = [];
}

function hidePortalMessageModal() {
  if (!(portalMessageModal instanceof HTMLElement) || portalMessageModal.hidden) {
    return;
  }
  portalMessageModal.hidden = true;
  restorePortalBackground();
  if (portalMessageOpener instanceof HTMLElement) {
    portalMessageOpener.focus();
  }
  portalMessageOpener = null;
}

function showPortalMessageModal(title, message, opener) {
  if (
    !(portalMessageModal instanceof HTMLElement)
    || !(portalMessageTitle instanceof HTMLElement)
    || !(portalMessageBody instanceof HTMLElement)
  ) {
    return;
  }

  portalMessageTitle.textContent = title;
  portalMessageBody.textContent = message;
  portalMessageOpener = opener instanceof HTMLElement ? opener : null;
  portalMessageModal.hidden = false;
  setPortalBackgroundInert();
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
      modalTrigger,
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
  if (!(portalMessageModal instanceof HTMLElement) || portalMessageModal.hidden) {
    return;
  }
  if (event.key === "Escape") {
    event.preventDefault();
    hidePortalMessageModal();
    return;
  }
  if (event.key !== "Tab") {
    return;
  }
  const focusableElements = [...portalMessageModal.querySelectorAll(
    "a[href], button:not([disabled]), input:not([disabled]), "
      + "select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])",
  )].filter((element) => element instanceof HTMLElement && !element.hidden);
  if (focusableElements.length === 0) {
    event.preventDefault();
    return;
  }
  const firstElement = focusableElements[0];
  const lastElement = focusableElements.at(-1);
  if (event.shiftKey && document.activeElement === firstElement) {
    event.preventDefault();
    lastElement.focus();
  } else if (!event.shiftKey && document.activeElement === lastElement) {
    event.preventDefault();
    firstElement.focus();
  }
});
