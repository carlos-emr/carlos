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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Document annotation overlay.
 *
 * Pages are server-rendered PNGs from ManageDocument?method=showPage. Marks are SVG
 * elements laid over each page image. Nothing here parses a PDF: the browser only ever
 * sees pictures, and Save posts a small JSON model of what was drawn. The server composes
 * the real document with PDFBox and files it as a new chart entry.
 *
 * Coordinates in the model are fractions of the displayed page, origin top left, so they
 * survive a zoom (which is only a re-request at a higher DPI) and match what the server
 * expects. See DocumentAnnotationDto for the shared contract.
 *
 * Vanilla JavaScript by project convention; no framework or library is loaded.
 */
(function () {
    'use strict';

    var cfg = window.CARLOS_ANNOTATE;
    if (!cfg) { return; }

    var SVG_NS = 'http://www.w3.org/2000/svg';
    var DPI_STEPS = [96, 144, 192];
    var COLORS = {
        yellow: '#FFF176', green: '#7BE8B8', blue: '#8FD3F4',
        pink: '#FFC2DD', red: '#E03B3B', black: '#1A1A1A'
    };

    var state = {
        tool: 'select',
        color: 'yellow',
        dpiIndex: 0,
        annotations: [],   // the model posted to the server
        wordBoxes: {},     // page -> [{x,y,w,h}], for snap-to-text
        seq: 0,
        saved: false
    };

    var pagesEl = document.getElementById('pages');
    var statusEl = document.getElementById('status');

    function t(key, fallback) {
        return (cfg.i18n && cfg.i18n[key]) ? cfg.i18n[key] : fallback;
    }

    function setStatus(message, kind) {
        statusEl.textContent = message || '';
        statusEl.className = 'status' + (kind ? ' ' + kind : '');
    }

    /* ---------- page scaffolding ---------- */

    function buildPages() {
        for (var page = 1; page <= cfg.pageCount; page++) {
            var wrap = document.createElement('div');
            wrap.className = 'page';
            wrap.dataset.page = String(page);

            var img = document.createElement('img');
            img.alt = t('pageLabel', 'Page') + ' ' + page;
            img.loading = page <= 2 ? 'eager' : 'lazy';
            img.dataset.page = String(page);
            img.addEventListener('load', function () { sizeOverlay(this.parentNode); });

            var svg = document.createElementNS(SVG_NS, 'svg');
            svg.setAttribute('class', 'overlay');
            svg.dataset.page = String(page);

            wrap.appendChild(img);
            wrap.appendChild(svg);

            var caption = document.createElement('div');
            caption.className = 'page-caption';
            caption.textContent = t('pageLabel', 'Page') + ' ' + page + ' / ' + cfg.pageCount;
            wrap.appendChild(caption);

            pagesEl.appendChild(wrap);
            attachPointer(wrap);
        }
        loadVisiblePages();
    }

    function pageImageUrl(page) {
        return cfg.contextPath + '/documentManager/ManageDocument?method=showPage'
            + '&doc_no=' + encodeURIComponent(cfg.docId)
            + '&page=' + encodeURIComponent(page)
            + '&dpi=' + encodeURIComponent(DPI_STEPS[state.dpiIndex]);
    }

    // Only fetch what is on screen (plus one screen of lead-in). A twenty page fax would
    // otherwise trigger twenty server renders the moment the viewer opens.
    function loadVisiblePages() {
        var images = pagesEl.querySelectorAll('img[data-page]');
        for (var i = 0; i < images.length; i++) {
            var img = images[i];
            var box = img.getBoundingClientRect();
            var near = box.top < window.innerHeight * 2 && box.bottom > -window.innerHeight;
            var wanted = pageImageUrl(img.dataset.page);
            if (near && img.getAttribute('src') !== wanted) {
                img.setAttribute('src', wanted);
            }
        }
    }

    function sizeOverlay(wrap) {
        var img = wrap.querySelector('img');
        var svg = wrap.querySelector('svg');
        if (!img || !svg || !img.clientWidth) { return; }
        svg.setAttribute('width', img.clientWidth);
        svg.setAttribute('height', img.clientHeight);
        svg.setAttribute('viewBox', '0 0 ' + img.clientWidth + ' ' + img.clientHeight);
        redrawPage(Number(wrap.dataset.page));
    }

    /* ---------- drawing the model ---------- */

    function redrawPage(page) {
        var wrap = pagesEl.querySelector('.page[data-page="' + page + '"]');
        if (!wrap) { return; }
        var svg = wrap.querySelector('svg');
        var img = wrap.querySelector('img');
        if (!svg || !img || !img.clientWidth) { return; }
        while (svg.firstChild) { svg.removeChild(svg.firstChild); }

        var w = img.clientWidth;
        var h = img.clientHeight;

        state.annotations.filter(function (a) { return a.page === page; }).forEach(function (a) {
            var el = renderMark(a, w, h);
            if (!el) { return; }
            el.setAttribute('data-id', a.id);
            el.addEventListener('click', function (event) {
                if (state.tool !== 'select') { return; }
                event.stopPropagation();
                removeAnnotation(a.id);
            });
            svg.appendChild(el);
        });
    }

    function renderMark(a, w, h) {
        if (a.type === 'ink') {
            var poly = document.createElementNS(SVG_NS, 'polyline');
            poly.setAttribute('points', a.points.map(function (p) {
                return (p[0] * w) + ',' + (p[1] * h);
            }).join(' '));
            poly.setAttribute('fill', 'none');
            poly.setAttribute('stroke', COLORS[a.color] || COLORS.black);
            poly.setAttribute('stroke-width', a.strokeWidth || 2);
            poly.setAttribute('stroke-linecap', 'round');
            poly.setAttribute('stroke-linejoin', 'round');
            poly.setAttribute('class', 'mark');
            return poly;
        }
        if (a.type === 'highlight') {
            var rect = document.createElementNS(SVG_NS, 'rect');
            rect.setAttribute('x', a.x * w);
            rect.setAttribute('y', a.y * h);
            rect.setAttribute('width', a.w * w);
            rect.setAttribute('height', a.h * h);
            rect.setAttribute('fill', COLORS[a.color] || COLORS.yellow);
            rect.setAttribute('fill-opacity', '0.38');
            rect.setAttribute('class', 'mark');
            return rect;
        }
        if (a.type === 'text' || a.type === 'date') {
            var text = document.createElementNS(SVG_NS, 'text');
            text.setAttribute('x', a.x * w);
            text.setAttribute('y', (a.y * h) + (a.fontSize || 11));
            text.setAttribute('fill', COLORS[a.color] || COLORS.black);
            text.setAttribute('font-size', (a.fontSize || 11));
            text.setAttribute('font-family', 'sans-serif');
            text.setAttribute('class', 'mark');
            text.textContent = a.text;
            return text;
        }
        if (a.type === 'signature') {
            var g = document.createElementNS(SVG_NS, 'g');
            g.setAttribute('class', 'mark');
            var box = document.createElementNS(SVG_NS, 'rect');
            box.setAttribute('x', a.x * w);
            box.setAttribute('y', a.y * h);
            box.setAttribute('width', a.w * w);
            box.setAttribute('height', a.h * h);
            box.setAttribute('fill', 'rgba(14,110,103,0.10)');
            box.setAttribute('stroke', '#0E6E67');
            box.setAttribute('stroke-dasharray', '4 3');
            var label = document.createElementNS(SVG_NS, 'text');
            label.setAttribute('x', (a.x * w) + 6);
            label.setAttribute('y', (a.y * h) + (a.h * h / 2) + 4);
            label.setAttribute('font-size', '12');
            label.setAttribute('font-family', 'sans-serif');
            label.setAttribute('fill', '#0E6E67');
            // The real stamp is drawn by the server at save time; this only shows placement.
            label.textContent = t('signatureHere', 'Signature');
            g.appendChild(box);
            g.appendChild(label);
            return g;
        }
        return null;
    }

    /* ---------- model edits ---------- */

    function addAnnotation(a) {
        a.id = ++state.seq;
        state.annotations.push(a);
        redrawPage(a.page);
        updateCounts();
    }

    function removeAnnotation(id) {
        var index = state.annotations.findIndex(function (a) { return a.id === id; });
        if (index < 0) { return; }
        var page = state.annotations[index].page;
        state.annotations.splice(index, 1);
        redrawPage(page);
        updateCounts();
    }

    function updateCounts() {
        var count = state.annotations.length;
        document.getElementById('markCount').textContent = String(count);
        document.getElementById('btnSave').disabled = count === 0;
        document.getElementById('btnSaveFax').disabled = count === 0;
    }

    /* ---------- pointer interaction ---------- */

    function attachPointer(wrap) {
        var svg = wrap.querySelector('svg');
        var page = Number(wrap.dataset.page);
        var dragging = null;

        svg.addEventListener('pointerdown', function (event) {
            if (state.tool === 'select') { return; }
            var rect = svg.getBoundingClientRect();
            var nx = (event.clientX - rect.left) / rect.width;
            var ny = (event.clientY - rect.top) / rect.height;

            if (state.tool === 'text' || state.tool === 'date' || state.tool === 'signature') {
                placePoint(page, nx, ny);
                return;
            }
            if (state.tool === 'highlight') { fetchWordBoxes(page); }
            dragging = { x0: nx, y0: ny, points: [[nx, ny]] };
            svg.setPointerCapture(event.pointerId);
        });

        svg.addEventListener('pointermove', function (event) {
            if (!dragging) { return; }
            var rect = svg.getBoundingClientRect();
            var nx = clamp((event.clientX - rect.left) / rect.width);
            var ny = clamp((event.clientY - rect.top) / rect.height);
            if (state.tool === 'draw') {
                dragging.points.push([nx, ny]);
            }
            dragging.x1 = nx;
            dragging.y1 = ny;
            previewDrag(svg, dragging);
        });

        svg.addEventListener('pointerup', function (event) {
            if (!dragging) { return; }
            svg.releasePointerCapture(event.pointerId);
            commitDrag(page, dragging);
            dragging = null;
            redrawPage(page);
        });
    }

    function previewDrag(svg, drag) {
        var existing = svg.querySelector('.preview');
        if (existing) { existing.remove(); }
        var w = svg.clientWidth;
        var h = svg.clientHeight;
        var el;
        if (state.tool === 'draw') {
            el = document.createElementNS(SVG_NS, 'polyline');
            el.setAttribute('points', drag.points.map(function (p) {
                return (p[0] * w) + ',' + (p[1] * h);
            }).join(' '));
            el.setAttribute('fill', 'none');
            el.setAttribute('stroke', COLORS[state.color]);
            el.setAttribute('stroke-width', '2');
        } else {
            el = document.createElementNS(SVG_NS, 'rect');
            el.setAttribute('x', Math.min(drag.x0, drag.x1 || drag.x0) * w);
            el.setAttribute('y', Math.min(drag.y0, drag.y1 || drag.y0) * h);
            el.setAttribute('width', Math.abs((drag.x1 || drag.x0) - drag.x0) * w);
            el.setAttribute('height', Math.abs((drag.y1 || drag.y0) - drag.y0) * h);
            el.setAttribute('fill', COLORS[state.color]);
            el.setAttribute('fill-opacity', '0.3');
        }
        el.setAttribute('class', 'preview');
        svg.appendChild(el);
    }

    function commitDrag(page, drag) {
        if (state.tool === 'draw') {
            if (drag.points.length < 2) { return; }
            addAnnotation({
                type: 'ink', page: page, color: state.color,
                strokeWidth: 2, points: simplify(drag.points)
            });
            return;
        }
        var x = Math.min(drag.x0, drag.x1 || drag.x0);
        var y = Math.min(drag.y0, drag.y1 || drag.y0);
        var w = Math.abs((drag.x1 || drag.x0) - drag.x0);
        var h = Math.abs((drag.y1 || drag.y0) - drag.y0);
        if (w < 0.004 || h < 0.004) { return; }

        var box = { x: x, y: y, w: w, h: h };
        if (state.tool === 'highlight') {
            box = snapToWords(page, box) || box;
            addAnnotation({
                type: 'highlight', page: page, color: state.color,
                x: box.x, y: box.y, w: box.w, h: box.h
            });
        }
    }

    function placePoint(page, nx, ny) {
        if (state.tool === 'signature') {
            addAnnotation({
                type: 'signature', page: page, color: 'black',
                x: clamp(nx, 0.7), y: clamp(ny, 0.08), w: 0.28, h: 0.07
            });
            return;
        }
        var isDate = state.tool === 'date';
        var value = isDate ? new Date().toISOString().slice(0, 10)
            : window.prompt(t('promptText', 'Note to add:'), '');
        if (!value) { return; }
        addAnnotation({
            type: isDate ? 'date' : 'text', page: page, color: state.color,
            x: clamp(nx, 0.6), y: clamp(ny, 0.04), w: 0.35, h: 0.035,
            text: value, fontSize: 11
        });
    }

    /* ---------- snap to text ---------- */

    /*
     * Snapping is a convenience layered on top of an OCR text layer, and the text layer is
     * OPTIONAL. A page can legitimately have none: a scan that was never run through OCR, a
     * photographed page, or an image-only fax. Every path below therefore returns null so
     * commitDrag falls back to the rectangle the provider actually drew. Highlighting, and
     * every other tool, works identically on a page with no text.
     */

    // Fraction of the drawn rectangle the snapped box must cover to be worth using. Sparse or
    // noisy OCR can put a single stray word inside a large drag; snapping to it would shrink
    // the provider's highlight to something they did not ask for, so below this the drawn
    // rectangle wins.
    var MIN_SNAP_COVERAGE = 0.25;

    function snapToWords(page, box) {
        var words = state.wordBoxes[page];
        // undefined  -> not fetched yet, 'pending' -> in flight, [] -> page has no text layer.
        if (!Array.isArray(words) || !words.length) { return null; }
        var hits = words.filter(function (word) {
            return word.x < box.x + box.w && word.x + word.w > box.x
                && word.y < box.y + box.h && word.y + word.h > box.y;
        });
        if (!hits.length) { return null; }
        var x0 = Math.min.apply(null, hits.map(function (w) { return w.x; }));
        var y0 = Math.min.apply(null, hits.map(function (w) { return w.y; }));
        var x1 = Math.max.apply(null, hits.map(function (w) { return w.x + w.w; }));
        var y1 = Math.max.apply(null, hits.map(function (w) { return w.y + w.h; }));

        var drawnArea = box.w * box.h;
        var snappedArea = (x1 - x0) * (y1 - y0);
        if (drawnArea > 0 && snappedArea / drawnArea < MIN_SNAP_COVERAGE) { return null; }

        return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
    }

    /*
     * Word boxes are fetched per page and only for pages in view. Requesting all of them when
     * the highlight tool is picked would fire one server-side text extraction per page, which
     * on a long fax is a burst of work for a feature the provider may not use on every page.
     */
    function fetchWordBoxes(page) {
        if (state.wordBoxes[page] !== undefined) { return; }
        state.wordBoxes[page] = 'pending';
        fetch(cfg.contextPath + '/documentManager/DocumentTextBoxes?docId='
            + encodeURIComponent(cfg.docId) + '&page=' + encodeURIComponent(page),
            { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                // An empty list is the normal answer for a page with no text layer, and is
                // cached as such so the page is not asked for again.
                state.wordBoxes[page] = (data && Array.isArray(data.words)) ? data.words : [];
            })
            .catch(function () {
                // A transient failure must not disable snapping for the rest of the session:
                // clearing the entry lets the next drag on this page try again. Until then
                // snapToWords returns null and the drawn rectangle is used.
                delete state.wordBoxes[page];
            });
    }

    /** Fetches word boxes for pages currently on screen, when the highlight tool is active. */
    function prefetchVisibleWordBoxes() {
        if (state.tool !== 'highlight') { return; }
        var wraps = pagesEl.querySelectorAll('.page');
        for (var i = 0; i < wraps.length; i++) {
            var rect = wraps[i].getBoundingClientRect();
            if (rect.top < window.innerHeight && rect.bottom > 0) {
                fetchWordBoxes(Number(wraps[i].dataset.page));
            }
        }
    }

    /* ---------- save ---------- */

    function csrfToken() {
        var input = document.querySelector('input[name="CSRF-TOKEN"]');
        return input ? input.value : '';
    }

    function save(thenFax) {
        if (!state.annotations.length) { return; }
        setStatus(t('saving', 'Saving…'), 'busy');
        document.getElementById('btnSave').disabled = true;
        document.getElementById('btnSaveFax').disabled = true;

        var payload = {
            annotations: state.annotations.map(function (a) {
                var out = { type: a.type, page: a.page, color: a.color };
                if (a.type === 'ink') {
                    out.points = a.points;
                    out.strokeWidth = a.strokeWidth;
                } else {
                    out.x = a.x; out.y = a.y; out.w = a.w; out.h = a.h;
                    if (a.text) { out.text = a.text; }
                    if (a.fontSize) { out.fontSize = a.fontSize; }
                }
                return out;
            })
        };

        fetch(cfg.contextPath + '/documentManager/SaveAnnotatedDocument?docId='
            + encodeURIComponent(cfg.docId), {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
                'CSRF-TOKEN': csrfToken()
            },
            body: JSON.stringify(payload)
        }).then(function (response) {
            return response.json().then(function (data) {
                return { ok: response.ok, data: data };
            });
        }).then(function (result) {
            if (!result.ok || !result.data.success) {
                setStatus(result.data && result.data.error
                    ? result.data.error
                    : t('saveFailed', 'The annotated document could not be saved.'), 'error');
                updateCounts();
                return;
            }
            state.saved = true;
            setStatus(t('saved', 'Saved as a new document.') + ' #' + result.data.documentNo, 'ok');
            if (thenFax) {
                window.location.href = cfg.contextPath + '/documentManager/FaxDocument?docId='
                    + encodeURIComponent(result.data.documentNo);
            } else {
                document.getElementById('savedLink').innerHTML = '';
                var link = document.createElement('a');
                link.href = cfg.contextPath + '/documentManager/ManageDocument?method=display&doc_no='
                    + encodeURIComponent(result.data.documentNo);
                link.target = '_blank';
                link.rel = 'noopener';
                link.textContent = t('openSaved', 'Open the saved copy');
                document.getElementById('savedLink').appendChild(link);
            }
        }).catch(function () {
            setStatus(t('saveFailed', 'The annotated document could not be saved.'), 'error');
            updateCounts();
        });
    }

    /* ---------- helpers ---------- */

    function clamp(value, span) {
        var max = span ? 1 - span : 1;
        return Math.max(0, Math.min(max, value));
    }

    // Freehand pointer events arrive far denser than the drawing needs; thinning keeps
    // the posted model well inside the server's per-stroke point ceiling.
    function simplify(points) {
        if (points.length <= 3) { return points; }
        var out = [points[0]];
        for (var i = 1; i < points.length - 1; i++) {
            var last = out[out.length - 1];
            if (Math.abs(points[i][0] - last[0]) > 0.002 || Math.abs(points[i][1] - last[1]) > 0.002) {
                out.push(points[i]);
            }
        }
        out.push(points[points.length - 1]);
        return out.slice(0, 4000);
    }

    /* ---------- wiring ---------- */

    function selectTool(tool) {
        state.tool = tool;
        var buttons = document.querySelectorAll('.tool');
        for (var i = 0; i < buttons.length; i++) {
            buttons[i].setAttribute('aria-pressed', String(buttons[i].dataset.tool === tool));
        }
        pagesEl.dataset.tool = tool;
        prefetchVisibleWordBoxes();
    }

    function zoom(direction) {
        var next = state.dpiIndex + direction;
        if (next < 0 || next >= DPI_STEPS.length) { return; }
        state.dpiIndex = next;
        var images = pagesEl.querySelectorAll('img[data-page]');
        for (var i = 0; i < images.length; i++) { images[i].removeAttribute('src'); }
        loadVisiblePages();
    }

    document.addEventListener('DOMContentLoaded', function () {
        buildPages();
        updateCounts();

        var tools = document.querySelectorAll('.tool');
        for (var i = 0; i < tools.length; i++) {
            tools[i].addEventListener('click', function () { selectTool(this.dataset.tool); });
        }
        var swatches = document.querySelectorAll('.swatch');
        for (var j = 0; j < swatches.length; j++) {
            swatches[j].addEventListener('click', function () {
                state.color = this.dataset.color;
                var all = document.querySelectorAll('.swatch');
                for (var k = 0; k < all.length; k++) {
                    all[k].setAttribute('aria-pressed', String(all[k].dataset.color === state.color));
                }
            });
        }
        document.getElementById('btnZoomIn').addEventListener('click', function () { zoom(1); });
        document.getElementById('btnZoomOut').addEventListener('click', function () { zoom(-1); });
        document.getElementById('btnSave').addEventListener('click', function () { save(false); });
        document.getElementById('btnSaveFax').addEventListener('click', function () { save(true); });

        window.addEventListener('scroll', function () {
            loadVisiblePages();
            prefetchVisibleWordBoxes();
        }, { passive: true });
        window.addEventListener('resize', function () {
            var wraps = pagesEl.querySelectorAll('.page');
            for (var n = 0; n < wraps.length; n++) { sizeOverlay(wraps[n]); }
        });
        window.addEventListener('beforeunload', function (event) {
            if (state.annotations.length && !state.saved) {
                event.preventDefault();
                event.returnValue = '';
            }
        });
        selectTool('select');
    });
})();
