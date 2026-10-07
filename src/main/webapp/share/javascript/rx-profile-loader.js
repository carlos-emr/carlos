/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* global CarlosAjax */
var RxProfileLoader = (function () {
    'use strict';
    var views = {
        current: [''],
        all: ['show=all'],
        active: ['status=active'],
        inactive: ['status=inactive'],
        longTermAcute: ['longTermOnly=true&heading=Long%20Term%20Meds', 'longTermOnly=acute&heading=Acute'],
        combined: ['longTermOnly=true&heading=Long%20Term%20Meds',
            'longTermOnly=acute&heading=Acute&status=active',
            'longTermOnly=acute&heading=Inactive&status=inactive',
            'heading=External&drugLocation=external']
    };

    function create(options) {
        var selected = 'current';
        var generation = 0;
        var pending = [];
        var index = 0;
        var busy = false;

        function next() {
            if (busy || index >= pending.length) return;
            busy = true;
            var requestGeneration = generation;
            var requestIndex = index;
            var query = pending[index];
            var settled = false;
            function complete(transport) {
                if (settled) return;
                settled = true;
                busy = false;
                // Updater has already inserted this response. A newer selection
                // stays hidden and replaces it before any result is displayed.
                if (requestGeneration !== generation) {
                    next();
                    return;
                }
                if (!(transport && transport.status >= 200 && transport.status < 300)) {
                    pending = [];
                    options.failed();
                    options.loading(false);
                    return;
                }
                index++;
                if (index < pending.length) next();
                else options.loading(false);
            }
            try {
                var xhr = options.updater({success: 'drugProfile'}, options.contextPath + '/rx/ViewListDrugs'
                    + (query ? '?' + query : ''), {
                    method: 'get',
                    parameters: {demographicNo: options.demographicNo, rand: Math.random()},
                    insertion: requestIndex ? 'bottom' : undefined,
                    evalScripts: true,
                    onComplete: complete
                });
                // CarlosAjax reports network errors through onComplete, but its
                // XHR has no default timeout/abort callback. Do not strand the queue.
                if (xhr && !settled) {
                    xhr.timeout = 30000;
                    xhr.ontimeout = xhr.onabort = function () { complete({status: 0}); };
                }
            } catch (error) {
                complete({status: 0});
            }
        }

        function select(view) {
            if (!Object.prototype.hasOwnProperty.call(views, view)) throw new Error('Unknown drug profile view');
            selected = view;
            generation++;
            pending = views[view];
            index = 0;
            options.loading(true);
            next();
        }
        return {select: select, refresh: function () { select(selected); }};
    }
    return {create: create};
}());
if (typeof module !== 'undefined' && module.exports) module.exports = RxProfileLoader;
