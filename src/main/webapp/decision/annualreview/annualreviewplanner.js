function restoreCheckboxStates() {
  const dataEl = document.getElementById('checked_fields');
  if (!dataEl) return;

  let checkedNames;
  try {
    // JSON.parse is not an HTML sink, preventing DOM-based XSS (js/xss-through-dom)
    checkedNames = JSON.parse(dataEl.textContent);
  } catch (e) {
    return;
  }

  if (!Array.isArray(checkedNames) || !checkedNames.length) return;

  // set matching checkboxes in one pass
  checkedNames.forEach(function(name) {
    if (typeof name !== 'string') return;
    // note the [i] for case-insensitive attr match in modern browsers
    const selector = 'input[type="checkbox"][name="' + CSS.escape(name) + '" i]';
    document.querySelectorAll(selector).forEach(cb => cb.checked = true);
  });
}

// attach once
window.addEventListener('load', restoreCheckboxStates, false);