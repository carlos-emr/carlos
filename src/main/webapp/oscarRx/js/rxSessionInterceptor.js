/**
 * AJAX Interceptor for Rx per-patient session isolation.
 *
 * Adds demographicNo to Prototype.js AJAX calls, jQuery AJAX calls, and
 * Rx-related form submissions so that RxSessionFilter (server-side) can
 * swap in the correct per-patient RxSessionBean for each request.
 *
 * Requires: var currentDemographicNo defined before this script loads.
 *
 * @since 2026-01-30
 */
(function() {
    'use strict';

    if (typeof currentDemographicNo === 'undefined' || !currentDemographicNo) {
        return;
    }

    var demoNo = String(currentDemographicNo);

    function hasDemographicNo(params) {
        if (!params) return false;
        if (typeof params === 'string') return params.indexOf('demographicNo=') !== -1;
        if (typeof params === 'object') return 'demographicNo' in params;
        return false;
    }

    function addDemographicNo(params) {
        if (!params) return 'demographicNo=' + demoNo;
        if (typeof params === 'string') {
            if (params.indexOf('demographicNo=') !== -1) return params;
            return params + '&demographicNo=' + demoNo;
        }
        if (typeof params === 'object') {
            if (typeof FormData !== 'undefined' && params instanceof FormData) {
                if (!params.has('demographicNo')) params.append('demographicNo', demoNo);
                return params;
            }
            if (typeof URLSearchParams !== 'undefined' && params instanceof URLSearchParams) {
                if (!params.has('demographicNo')) params.set('demographicNo', demoNo);
                return params;
            }
            params.demographicNo = demoNo;
            return params;
        }
        return params;
    }

    // Intercept Prototype.js Ajax.Request by wrapping initialize on the
    // prototype instead of replacing the constructor. This preserves static
    // properties like Ajax.Request.Events that prototype.js depends on.
    if (typeof Ajax !== 'undefined' && Ajax.Request && Ajax.Request.prototype.initialize) {
        var origRequestInit = Ajax.Request.prototype.initialize;
        Ajax.Request.prototype.initialize = function(url, options) {
            options = options || {};
            if (!hasDemographicNo(options.parameters)) {
                options.parameters = addDemographicNo(options.parameters);
            }
            if (options.postBody && !hasDemographicNo(options.postBody)) {
                options.postBody = addDemographicNo(options.postBody);
            }
            origRequestInit.call(this, url, options);
        };
    }

    // Intercept Prototype.js Ajax.Updater (extends Ajax.Request, so wrapping
    // Ajax.Request.prototype.initialize covers most cases, but Updater has
    // its own initialize that calls Ajax.Request with modified options)
    if (typeof Ajax !== 'undefined' && Ajax.Updater && Ajax.Updater.prototype.initialize) {
        var origUpdaterInit = Ajax.Updater.prototype.initialize;
        Ajax.Updater.prototype.initialize = function(container, url, options) {
            options = options || {};
            if (!hasDemographicNo(options.parameters)) {
                options.parameters = addDemographicNo(options.parameters);
            }
            origUpdaterInit.call(this, container, url, options);
        };
    }

    // Intercept jQuery AJAX
    if (typeof jQuery !== 'undefined') {
        jQuery.ajaxPrefilter(function(options) {
            if (!hasDemographicNo(options.data)) {
                options.data = addDemographicNo(options.data);
            }
        });
    }

    // Add hidden demographicNo field to Rx-related forms that don't have one
    function processAllForms() {
        var forms = document.querySelectorAll('form');
        for (var i = 0; i < forms.length; i++) {
            var form = forms[i];
            if (form.querySelector('input[name="demographicNo"]')) continue;
            var action = form.getAttribute('action') || '';
            if (action.indexOf('/oscarRx/') === -1 && action.indexOf('Rx') === -1 && action !== '') continue;
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'demographicNo';
            input.value = demoNo;
            form.appendChild(input);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', processAllForms);
    } else {
        processAllForms();
    }
})();
