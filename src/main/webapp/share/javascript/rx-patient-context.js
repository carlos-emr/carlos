/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/**
 * Tags every prescription (Rx) request made from an Rx page with the page's patient.
 *
 * The server keeps one prescription session bean per patient and picks it from the request's
 * demographicNo (RxSessionBeanResolver). Every patient-specific request must explicitly name it.
 * Loading this script with data-demographic-no and optional data-chart-context preserves the
 * page's patient and originating chart program through:
 *   - CarlosAjax.request / CarlosAjax.updater calls,
 *   - jQuery.ajax calls,
 *   - rx.js popupWindow() and Oscar.js popup2() popups,
 *   - form submissions (a hidden input is added on submit),
 *   - followed links (the href is tagged on click),
 * when the target is an Rx route (/rx/...). Existing explicit identities are retained, and chart
 * context is only added for this page's patient.
 *
 * Load it after carlos-ajax.js, jQuery and rx.js. Use RxPatientContext.withPatient(url) for URLs
 * built by hand, such as a form that is submitted programmatically.
 */
(function (root) {
    'use strict';

    var PATIENT_PARAMETER = /(^|[?&])(demographicNo|demographic_no)=/;

    function currentScriptDemographicNo() {
        var script = typeof document !== 'undefined' ? document.currentScript : null;
        var value = script && script.getAttribute ? script.getAttribute('data-demographic-no') : null;
        return value && /^\d+$/.test(value) ? value : null;
    }

    function currentScriptChartContext() {
        var script = typeof document !== 'undefined' ? document.currentScript : null;
        return script && script.getAttribute ? script.getAttribute('data-chart-context') : null;
    }

    function isRxUrl(url) {
        if (typeof url !== 'string') {
            return false;
        }
        // Browsers drop leading whitespace before resolving an href.
        var candidate = url.trim();
        // URL parsers remove embedded tabs and newlines before recognizing a scheme/host.
        // Reject controls before our lexical checks so an obfuscated external URL cannot
        // acquire the patient's id (for example ht\ttps://other.example/rx/...).
        if (candidate === '' || /[\u0000-\u001f\u007f]/.test(candidate)) {
            return false;
        }
        // The patient id must never leave this origin. A protocol-relative URL (//host/rx/...,
        // and the /\ and \\ spellings browsers treat the same way) names another host with no
        // scheme, so it is refused outright.
        if (/^[\\/]{2}/.test(candidate)) {
            return false;
        }
        // Any URL with a scheme is tagged only when it is http(s) on this page's own origin;
        // javascript:, data:, mailto: and cross-origin URLs are never tagged.
        if (/^[a-z][a-z0-9+.-]*:/i.test(candidate)) {
            if (!/^https?:/i.test(candidate) || typeof location === 'undefined' || !location.origin
                    || typeof URL !== 'function') {
                return false;
            }
            var origin;
            try {
                origin = new URL(candidate).origin;
            } catch (e) {
                return false;
            }
            if (origin !== location.origin) {
                return false;
            }
        }
        var path = candidate.split('#')[0].split('?')[0];
        return path.indexOf('/rx/') >= 0 || path.indexOf('rx/') === 0;
    }

    /** Adds the missing patient and originating chart context to a same-origin Rx request. */
    function tag(url, demographicNo, chartContext, data) {
        if (!demographicNo || !isRxUrl(url)) {
            return url;
        }
        var hashIndex = url.indexOf('#');
        var hash = hashIndex >= 0 ? url.slice(hashIndex) : '';
        var base = hashIndex >= 0 ? url.slice(0, hashIndex) : url;
        var query = base.indexOf('?') >= 0 ? base.slice(base.indexOf('?') + 1) : '';
        var extra = [];
        if (!PATIENT_PARAMETER.test(query) && !bodyNamesPatient(data)) {
            extra.push('demographicNo=' + encodeURIComponent(demographicNo));
        }
        if (chartContext && !/(^|&)chartContext=/.test(query) && parameterValues(data, 'chartContext').length === 0
                && namesSamePatient(query, demographicNo) && namesSamePatient(data, demographicNo)) {
            extra.push('chartContext=' + encodeURIComponent(chartContext));
        }
        if (!extra.length) return url;
        var separator = base.indexOf('?') >= 0 ? (base.endsWith('?') || base.endsWith('&') ? '' : '&') : '?';
        return base + separator + extra.join('&') + hash;
    }

    function parameterValues(data, name) {
        if (data == null) return [];
        if (typeof data === 'string') return new URLSearchParams(data).getAll(name);
        if (typeof data.getAll === 'function') return data.getAll(name);
        if (typeof data === 'object' && Object.prototype.hasOwnProperty.call(data, name)) {
            // CarlosAjax encodes an explicitly supplied empty array as an empty parameter.
            return Array.isArray(data[name]) && data[name].length ? data[name] : [data[name]];
        }
        return [];
    }

    function namesSamePatient(data, demographicNo) {
        return ['demographicNo', 'demographic_no'].every(function (name) {
            return parameterValues(data, name).every(function (value) {
                return isSamePatient(value, demographicNo);
            });
        });
    }

    function isSamePatient(value, demographicNo) {
        // An absent parameter can inherit this page's patient; an explicitly blank or malformed
        // value cannot. ChartProgramContext validates every submitted value with this grammar.
        return value != null && /^[0-9]{1,10}$/.test(String(value))
            && Number(value) === Number(demographicNo);
    }

    /**
     * Whether a request body already names a patient. The server resolves the patient from every
     * demographicNo value it receives, so naming it in both the URL and the body is harmless there,
     * but Struts binds a repeated parameter into a typed action property as an array; that is a
     * conversion error, and the action answers with its missing "input" result (HTTP 404). So a
     * request whose body names its patient is never tagged a second time on the URL.
     */
    function bodyNamesPatient(data) {
        if (data == null) {
            return false;
        }
        if (typeof data === 'string') {
            return PATIENT_PARAMETER.test(data);
        }
        if ((typeof URLSearchParams !== 'undefined' && data instanceof URLSearchParams)
                || (typeof FormData !== 'undefined' && data instanceof FormData)) {
            return data.has('demographicNo') || data.has('demographic_no');
        }
        if (typeof data === 'object') {
            return Object.prototype.hasOwnProperty.call(data, 'demographicNo')
                || Object.prototype.hasOwnProperty.call(data, 'demographic_no');
        }
        return false;
    }

    /** The data a CarlosAjax call sends: postBody wins over parameters, as in carlos-ajax.js. */
    function carlosAjaxData(options) {
        if (!options) {
            return null;
        }
        return options.postBody != null ? options.postBody : options.parameters;
    }

    /**
     * The per-window holder of the patient to tag with. The wrappers are installed once per
     * window (their globals may outlive a page, e.g. a CarlosAjax or jQuery shared across frames),
     * so they read the patient from here at call time instead of closing over the patient of the
     * page that installed them; every install() then points them at the current page's patient.
     */
    function stateFor(win) {
        var state = win.__rxPatientContextState;
        if (!state || typeof state !== 'object') {
            state = {demographicNo: null, chartContext: null};
            win.__rxPatientContextState = state;
        }
        return state;
    }

    function create(demographicNo, chartContext) {
        function withPatient(url) {
            return tag(url, demographicNo, chartContext);
        }

        function install(win) {
            if (!win) {
                return;
            }
            var state = stateFor(win);
            // The latest page wins, including a page with no patient: its calls must not carry
            // the previous page's patient.
            state.demographicNo = demographicNo;
            state.chartContext = chartContext;
            if (!demographicNo) {
                return;
            }
            function current(url, data) {
                return tag(url, state.demographicNo, state.chartContext, data);
            }
            var ajax = win.CarlosAjax;
            if (ajax && !ajax.__rxPatientContext) {
                var request = ajax.request;
                var updater = ajax.updater;
                ajax.request = function (url, options) {
                    return request.call(this, current(url, carlosAjaxData(options)), options);
                };
                ajax.updater = function (container, url, options) {
                    return updater.call(this, container, current(url, carlosAjaxData(options)), options);
                };
                ajax.__rxPatientContext = true;
            }
            var jq = win.jQuery;
            if (jq && typeof jq.ajaxPrefilter === 'function' && !jq.__rxPatientContext) {
                jq.ajaxPrefilter(function (options) {
                    options.url = current(options.url, options.data);
                });
                jq.__rxPatientContext = true;
            }
            if (typeof win.popupWindow === 'function' && !win.popupWindow.__rxPatientContext) {
                var popup = win.popupWindow;
                win.popupWindow = function (height, width, url, name) {
                    return popup.call(this, height, width, current(url), name);
                };
                win.popupWindow.__rxPatientContext = true;
            }
            // Oscar.js popup2(height, width, top, left, url, windowName): the Rx search and reason
            // popups on the staging page open through it, and popup() delegates to it too.
            // popup2 keeps its window registry on the global it is called through
            // (context.popup2.winRefs), so the wrapper simply becomes that global.
            if (typeof win.popup2 === 'function' && !win.popup2.__rxPatientContext) {
                var popup2 = win.popup2;
                win.popup2 = function (height, width, top, left, url, name) {
                    return popup2.call(this, height, width, top, left, current(url), name);
                };
                win.popup2.__rxPatientContext = true;
            }
            function tagForm(form) {
                if (!state.demographicNo || !form || !form.getAttribute
                        || !isRxUrl(form.getAttribute('action') || '')) return;
                var namedPatients = form.querySelectorAll('[name="demographicNo"], [name="demographic_no"]');
                if (!namedPatients.length) {
                    var input = win.document.createElement('input');
                    input.type = 'hidden';
                    input.name = 'demographicNo';
                    input.value = state.demographicNo;
                    form.appendChild(input);
                }
                if (state.chartContext && Array.prototype.every.call(namedPatients, function (input) {
                            return isSamePatient(input.value, state.demographicNo);
                        })
                        && !form.querySelector('[name="chartContext"]')) {
                    var contextInput = win.document.createElement('input');
                    contextInput.type = 'hidden';
                    contextInput.name = 'chartContext';
                    contextInput.value = state.chartContext;
                    form.appendChild(contextInput);
                }
            }
            // Native form.submit() skips the submit event. Prepare the forms already rendered,
            // and repeat at DOMContentLoaded for forms appearing later in the document.
            if (win.document && typeof win.document.querySelectorAll === 'function') {
                Array.prototype.forEach.call(win.document.querySelectorAll('form'), tagForm);
            }
            if (win.document && win.document.addEventListener && !win.document.__rxPatientContext) {
                win.document.__rxPatientContext = true;
                win.document.addEventListener('submit', function (event) {
                    tagForm(event.target);
                }, true);
                // Plain links (drug profile, breadcrumbs, the static-script view) are navigations,
                // which the wrappers above never see. Tag the href as the link is followed.
                win.document.addEventListener('click', function (event) {
                    var target = event.target;
                    var link = target && target.closest ? target.closest('a[href]') : null;
                    if (!link) {
                        return;
                    }
                    var href = link.getAttribute('href');
                    var tagged = current(href);
                    if (tagged !== href) {
                        link.setAttribute('href', tagged);
                    }
                }, true);
            }
        }

        return {demographicNo: demographicNo, chartContext: chartContext, withPatient: withPatient, install: install};
    }

    var api = {create: create, isRxUrl: isRxUrl, bodyNamesPatient: bodyNamesPatient};
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    if (root && root.document) {
        var context = create(currentScriptDemographicNo(), currentScriptChartContext());
        root.RxPatientContext = context;
        context.install(root);
        // rx.js, Oscar.js and page scripts may define popupWindow / popup2 after this script runs.
        root.document.addEventListener('DOMContentLoaded', function () {
            context.install(root);
        });
    }
})(typeof window !== 'undefined' ? window : null);
