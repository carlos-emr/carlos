/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/** Render the current tickler validation attempt as inert, separate message lines. */
var CarlosTicklerValidation = (function () {
    'use strict';
    function reset() {
        var alert = document.getElementById('error');
        alert.textContent = '';
        alert.style.display = 'none';
    }
    function show(message) {
        var alert = document.getElementById('error');
        var line = document.createElement('div');
        line.className = 'tickler-validation-message';
        line.textContent = message;
        alert.appendChild(line);
        alert.style.display = 'block';
    }
    return { reset: reset, show: show };
}());
