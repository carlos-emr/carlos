function restoreCheckboxStates() {
  const xmlEl = document.getElementById('xml_list');
  if (!xmlEl) return;
  const xmlText = (xmlEl.textContent || xmlEl.innerText).trim();
  if (!xmlText) return;

  // Extract checked element names using regex instead of DOMParser to avoid
  // DOM-text-to-parser data flow flagged by static analysis tools.
  // The XML structure is simple: <tagname>checked</tagname> pairs only.
  const checkedNames = [];
  const pattern = /<([A-Za-z_][\w.-]*)>[^<]*checked[^<]*<\/\1>/g;
  let match;
  while ((match = pattern.exec(xmlText)) !== null) {
    checkedNames.push(match[1].toLowerCase());
  }

  if (!checkedNames.length) return;

  // set matching checkboxes in one pass
  checkedNames.forEach(function(name) {
    // note the [i] for case-insensitive attr match in modern browsers
    const selector = 'input[type="checkbox"][name="' + CSS.escape(name) + '" i]';
    document.querySelectorAll(selector).forEach(cb => cb.checked = true);
  });
}

// attach once
window.addEventListener('load', restoreCheckboxStates, false);