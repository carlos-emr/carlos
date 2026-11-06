/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function () {
    'use strict';

    async function displayReport() {
        const output = document.getElementById('MOHreport');
        const error = document.getElementById('MOHreportError');
        const source = document.getElementById('MOHreportSource');
        try {
            const parser = new DOMParser();
            const xml = parser.parseFromString(source.value, 'application/xml');
            if (xml.querySelector('parsererror') || xml.documentElement.nodeName !== 'REPORT') {
                throw new Error('Invalid MOH XML report');
            }
            const response = await fetch(source.dataset.stylesheet, { credentials: 'same-origin' });
            if (!response.ok) throw new Error('MOH stylesheet unavailable');
            const stylesheet = parser.parseFromString(await response.text(), 'application/xml');
            if (stylesheet.querySelector('parsererror')) throw new Error('Invalid MOH stylesheet');
            const processor = new XSLTProcessor();
            processor.importStylesheet(stylesheet);
            const fragment = processor.transformToFragment(xml, document);
            if (!fragment || !fragment.hasChildNodes()) throw new Error('Empty MOH report');
            output.replaceChildren(fragment);
            error.hidden = true;
        } catch (_) {
            // Never leave an apparently successful blank page when the file, stylesheet,
            // or browser's transformation support fails. Do not expose report content.
            output.replaceChildren();
            error.hidden = false;
        }
    }

    document.addEventListener('DOMContentLoaded', displayReport);
}());
