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
 * demographicNo (RxSessionBeanResolver). Many legacy Rx calls send no demographicNo; without it
 * the server falls back to the patient whose Rx page was opened last, which is another patient
 * when two charts are open side by side (#3875). Loading this script on an Rx page, with
 * data-demographic-no on the script tag, adds demographicNo to:
 *   - CarlosAjax.request / CarlosAjax.updater calls,
 *   - jQuery.ajax calls,
 *   - rx.js popupWindow() popups,
 *   - form submissions (a hidden input is added on submit),
 * when the target is an Rx route (/rx/...) and does not already name a patient.
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

    function isRxUrl(url) {
        if (typeof url !== 'string' || url === '') {
            return false;
        }
        var path = url.split('#')[0].split('?')[0];
        // Absolute URLs to another origin are never tagged.
        if (/^[a-z][a-z0-9+.-]*:\/\//i.test(path)) {
            if (typeof location === 'undefined' || path.indexOf(location.origin + '/') !== 0) {
                return false;
            }
        }
        return path.indexOf('/rx/') >= 0 || path.indexOf('rx/') === 0;
    }

    function create(demographicNo) {
        function withPatient(url) {
            if (!demographicNo || !isRxUrl(url)) {
                return url;
            }
            var hashIndex = url.indexOf('#');
            var hash = hashIndex >= 0 ? url.slice(hashIndex) : '';
            var base = hashIndex >= 0 ? url.slice(0, hashIndex) : url;
            var query = base.indexOf('?') >= 0 ? base.slice(base.indexOf('?') + 1) : '';
            if (PATIENT_PARAMETER.test(query)) {
                return url;
            }
            var separator = base.indexOf('?') >= 0 ? (base.endsWith('?') || base.endsWith('&') ? '' : '&') : '?';
            return base + separator + 'demographicNo=' + encodeURIComponent(demographicNo) + hash;
        }

        function install(win) {
            if (!demographicNo || !win) {
                return;
            }
            var ajax = win.CarlosAjax;
            if (ajax && !ajax.__rxPatientContext) {
                var request = ajax.request;
                var updater = ajax.updater;
                ajax.request = function (url, options) {
                    return request.call(this, withPatient(url), options);
                };
                ajax.updater = function (container, url, options) {
                    return updater.call(this, container, withPatient(url), options);
                };
                ajax.__rxPatientContext = true;
            }
            var jq = win.jQuery;
            if (jq && typeof jq.ajaxPrefilter === 'function' && !jq.__rxPatientContext) {
                jq.ajaxPrefilter(function (options) {
                    options.url = withPatient(options.url);
                });
                jq.__rxPatientContext = true;
            }
            if (typeof win.popupWindow === 'function' && !win.popupWindow.__rxPatientContext) {
                var popup = win.popupWindow;
                win.popupWindow = function (height, width, url, name) {
                    return popup.call(this, height, width, withPatient(url), name);
                };
                win.popupWindow.__rxPatientContext = true;
            }
            if (win.document && win.document.addEventListener && !win.document.__rxPatientContext) {
                win.document.__rxPatientContext = true;
                win.document.addEventListener('submit', function (event) {
                    var form = event.target;
                    if (!form || !form.getAttribute || !isRxUrl(form.getAttribute('action') || '')) {
                        return;
                    }
                    if (form.querySelector('[name="demographicNo"], [name="demographic_no"]')) {
                        return;
                    }
                    var input = win.document.createElement('input');
                    input.type = 'hidden';
                    input.name = 'demographicNo';
                    input.value = demographicNo;
                    form.appendChild(input);
                }, true);
            }
        }

        return {demographicNo: demographicNo, withPatient: withPatient, install: install};
    }

    var api = {create: create, isRxUrl: isRxUrl};
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    if (root && root.document) {
        var context = create(currentScriptDemographicNo());
        root.RxPatientContext = context;
        context.install(root);
        // rx.js and page scripts may define popupWindow after this script runs.
        root.document.addEventListener('DOMContentLoaded', function () {
            context.install(root);
        });
    }
})(typeof window !== 'undefined' ? window : null);
