/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function (root) {
    'use strict';
    function providerUrl(url, provider, base) {
        if (!provider) return url;
        var target = new URL(url, base);
        target.searchParams.set('dashboardProviderNo', provider);
        return target.href;
    }
    if (typeof module === 'object' && module.exports) module.exports = {providerUrl: providerUrl};
    if (root && root.document) {
        root.dashboardProviderUrl = function (url) {
            var field = root.document.getElementById('dashboardProviderNo');
            return providerUrl(url, field && field.value, root.location.href);
        };
    }
})(typeof window === 'undefined' ? null : window);
