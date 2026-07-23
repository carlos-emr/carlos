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
