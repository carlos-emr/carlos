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
  if (!(target instanceof HTMLInputElement)) {
    return;
  }

  target.select();
  target.setSelectionRange(0, target.value.length);
  if (!navigator.clipboard) {
    return;
  }

  void navigator.clipboard.writeText(target.value).then(() => {
    const originalText = copyButton.textContent || "Copy";
    copyButton.textContent = "Copied";
    window.setTimeout(() => {
      copyButton.textContent = originalText;
    }, 1500);
  });
});

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
