function restoreCheckboxStates() {
  const xmlEl = document.getElementById('xml_list');
  if (!xmlEl) return;
  const xmlText = (xmlEl.textContent || xmlEl.innerText).trim();
  if (!xmlText) return;

  // parse XML
  const doc = new DOMParser().parseFromString(xmlText, 'application/xml');
  if (doc.getElementsByTagName('parsererror').length) return;

  // collect all tags whose text == "checked"
  const checkedNames = Array.prototype.filter
    .call(doc.documentElement.children, node => node.textContent === 'checked')
    .map(node => node.nodeName.toLowerCase());

  if (!checkedNames.length) return;

  // set matching checkboxes in one pass
  checkedNames.forEach(function(name) {
    // note the [i] for case-insensitive attr match in modern browsers
    const selector = 'input[type="checkbox"][name="' + name + '" i]';
    document.querySelectorAll(selector).forEach(cb => cb.checked = true);
  });
}

// attach once
if (window.addEventListener) {
  window.addEventListener('load', restoreCheckboxStates, false);
} else {
  window.attachEvent('onload', restoreCheckboxStates);
}