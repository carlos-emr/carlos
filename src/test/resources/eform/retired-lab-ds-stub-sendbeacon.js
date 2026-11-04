(function(global) {
  var warned = false;
  function warnStub(fnName) {
    if (warned) { return; }
    if (global.console && console.warn) {
      console.warn("CARLOS: a lab decision-support script is not deployed; its functions are stubbed and do nothing. Contact your administrator if lab decision support is expected.");
    }
    // A console warning is invisible to clinicians and to the headless PDF renderer.
    // On the FIRST invocation of any stubbed function, make the degradation visible at
    // the point of use (an in-form banner) and durable for operators (a server beacon),
    // so a silently-disabled reminder/tickler is not mistaken for a completed action.
    try {
      var doc = global.document;
      if (doc && doc.body && doc.createElement) {
        // Latch only once the banner is actually in the DOM. Setting it earlier meant a
        // first stub call from an inline <head> script (doc.body still null -- normal for
        // this corpus) permanently suppressed both the banner and the beacon, leaving a
        // console-only trace no clinician or renderer can see.
        warned = true;
        var banner = doc.createElement('div');
        banner.setAttribute('id', 'carlos-lab-ds-unavailable');
        banner.textContent = 'Lab decision support is not available on this server \u2014 automated reminders/ticklers on this form will not run. Contact your administrator.';
        banner.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99999;background:#fdecea;color:#611a15;padding:8px 12px;font:14px sans-serif;border-bottom:2px solid #f5c6cb;';
        doc.body.appendChild(banner);
      }
      var ctxEl = doc && doc.getElementById ? doc.getElementById('context') : null;
      var fidEl = doc && doc.getElementById ? doc.getElementById('fid') : null;
      var ctx = ctxEl ? ctxEl.value : '';
      if (ctx && global.navigator && global.navigator.sendBeacon && global.URLSearchParams) {
        var params = new global.URLSearchParams();
        params.append('formId', fidEl ? fidEl.value : '');
        params.append('error', 'lab decision-support stub invoked (not deployed): ' + (fnName || 'unknown'));
        global.navigator.sendBeacon(ctx + '/eform/logEformError', params);
      }
    } catch (e) { /* best-effort visibility only; never break the form */ }
  }
  // Bind the name so the beacon reports which stub ran; an unbound warnStub() call
  // always reported 'unknown' and told an operator nothing about which script is missing.
  function noop(name) { return function () { warnStub(name); return null; }; }
  function falsy(name) { return function () { warnStub(name); return false; }; }
  global.CheckCopyTo = global.CheckCopyTo || noop('CheckCopyTo');
  global.Reminders = global.Reminders || noop('Reminders');
  global.ToggleCopyTo = global.ToggleCopyTo || noop('ToggleCopyTo');
  global.autoLabReqPop = global.autoLabReqPop || noop('autoLabReqPop');
  global.calculateTicklerDays = global.calculateTicklerDays || noop('calculateTicklerDays');
  global.decisionSupport = global.decisionSupport || noop('decisionSupport');
  global.population = global.population || noop('population');
  global.sendTickler = global.sendTickler || falsy('sendTickler');
  global.setLabLocation = global.setLabLocation || noop('setLabLocation');
  global.ticklerReminder = global.ticklerReminder || noop('ticklerReminder');
  // Render-gate marker. The stub is deployed under the REAL script filename, so the
  // request returns 200 and the renderer's network scan cannot tell a stubbed form from
  // a working one -- a requisition would fax with unpopulated fields and no tickler
  // while the render reported complete. Mirrors the signature path's
  // #carlos-signature-unrendered marker, which readPageGeometry already reports.
  try {
    var markerDoc = global.document;
    if (markerDoc && markerDoc.createElement && !markerDoc.getElementById('carlos-lab-ds-stubbed')) {
      var marker = markerDoc.createElement('div');
      marker.setAttribute('id', 'carlos-lab-ds-stubbed');
      marker.style.cssText = 'display:none';
      (markerDoc.body || markerDoc.documentElement).appendChild(marker);
    }
  } catch (e) { /* marker is best-effort; never break the form */ }
})(window);
