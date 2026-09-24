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
    // Default freehand stroke, in PDF points. Must match DocumentAnnotationParser's
    // DEFAULT_STROKE_WIDTH: the preview, the committed mark and the composed PDF all scale
    // from this one value.
    var DEFAULT_STROKE_WIDTH = 2;

    /** Keep in step with AnnotatedDocumentComposer.TEXT_BASELINE_RATIO. */
    var TEXT_BASELINE_RATIO = 0.78;

    var SIGNATURE_W = 0.28;
    var SIGNATURE_H = 0.07;
    var TEXT_W = 0.35;
    var TEXT_H = 0.035;

    var DPI_STEPS = [96, 144, 192];
    // Pointer travel, in CSS pixels, before a press on a mark becomes a move rather than a click.
    var MOVE_THRESHOLD_PX = 4;
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
        saved: false,
        saving: false,
        uncertain: false,
        moves: 0,            // marks being dragged now; pages track their own gestures
        previews: {},        // mark id -> {dx, dy} page fractions of a drag not yet released
        refitPending: false, // the annotation font arrived mid-drag; refit notes when it ends
        fontReady: null      // settles once the annotation font has loaded or failed
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
            img.addEventListener('load', function () {
                this.parentNode.classList.remove('load-failed');
                sizeOverlay(this.parentNode);
            });
            img.addEventListener('error', function () {
                this.parentNode.classList.add('load-failed');
                setStatus('Page ' + this.dataset.page + ' could not be loaded. Reload the viewer before annotating it.', 'error');
            });

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
        var scale = pxPerPoint(img);

        state.annotations.filter(function (a) { return a.page === page; }).forEach(function (a) {
            var el = renderMark(a, w, h, scale);
            if (!el) { return; }
            el.setAttribute('data-id', a.id);
            el.setAttribute('data-kind', isPlaced(a) ? 'placed' : 'stroke');
            svg.appendChild(el);
            if (isEditableText(a)) { addNoteHitArea(svg, el, a.id); }
        });
        // A redraw during a drag (a resize, the annotation font arriving) replaces the dragged
        // mark's elements; put its preview back so the mark stays under the pointer.
        Object.keys(state.previews).forEach(function (id) {
            applyPreview(svg, id, state.previews[id], w, h);
        });
    }

    /** Shifts every element drawn for a mark by a drag preview given in page fractions. */
    function applyPreview(svg, id, preview, w, h) {
        var shift = 'translate(' + (preview.dx * w) + ',' + (preview.dy * h) + ')';
        Array.prototype.forEach.call(svg.querySelectorAll('[data-id="' + id + '"]'), function (el) {
            el.setAttribute('transform', shift);
            el.classList.add('moving');
        });
    }

    /**
     * A transparent box behind a note, over its measured text bounds. SVG text is otherwise
     * hit-tested on glyph outlines only, so a press between two letters would miss the note
     * and place a new one instead; pointer-events: bounding-box would fix that, but Firefox
     * and Safari ignore it. The box carries the note's data-id, so it grabs and moves the note.
     */
    function addNoteHitArea(svg, textEl, id) {
        var box;
        var width;
        try {
            box = textEl.getBBox();
            width = advanceWidth(textEl);
        } catch (e) { return; }
        if (!box || !width || !box.height) { return; }
        var hit = document.createElementNS(SVG_NS, 'rect');
        hit.setAttribute('x', box.x);
        hit.setAttribute('y', box.y);
        // The full advance, so a press on a note's trailing spaces still grabs it.
        hit.setAttribute('width', width);
        hit.setAttribute('height', box.height);
        // 'transparent' is still a paint, so the default visiblePainted hit-testing counts it.
        hit.setAttribute('fill', 'transparent');
        hit.setAttribute('class', 'mark-hit');
        hit.setAttribute('data-id', id);
        svg.insertBefore(hit, textEl);
    }

    /**
     * Displayed CSS pixels per PDF point for this page image.
     *
     * Positions in the model are normalised 0..1, so they need no conversion — but fontSize and
     * strokeWidth are ABSOLUTE, and the composer draws them in PDF points
     * (AnnotatedDocumentComposer setLineWidth / setTextMatrix). Drawing the same numbers as SVG
     * units would size them in displayed pixels instead, which is a different unit and a
     * different amount at every zoom level and window width: an 11 pt note previewed on a
     * 96 dpi render appears ~1.33x smaller than it will be filed, so a note a provider fits
     * into the gap between two printed lab lines overlaps the values in the saved copy.
     *
     * naturalHeight is the rendered image in device pixels = pagePoints * dpi / 72, so
     * pxPerPoint = clientHeight / (naturalHeight * 72 / dpi).
     */
    function pxPerPoint(img) {
        var dpi = DPI_STEPS[state.dpiIndex] || 96;
        if (!img.naturalHeight || !img.clientHeight) { return 1; }
        return (img.clientHeight * dpi) / (img.naturalHeight * 72);
    }

    function renderMark(a, w, h, scale) {
        var unit = scale || 1;
        if (a.type === 'ink') {
            var points = a.points.map(function (p) {
                return (p[0] * w) + ',' + (p[1] * h);
            }).join(' ');
            var stroke = document.createElementNS(SVG_NS, 'g');
            stroke.setAttribute('class', 'mark');
            // A 2 pt line is too thin to grab reliably with a mouse, let alone a finger. This
            // wider, invisible copy is the hit target for moving it; it is never posted.
            var hit = document.createElementNS(SVG_NS, 'polyline');
            hit.setAttribute('points', points);
            hit.setAttribute('fill', 'none');
            hit.setAttribute('stroke', 'transparent');
            hit.setAttribute('stroke-width', Math.max(12, (a.strokeWidth || DEFAULT_STROKE_WIDTH) * unit));
            hit.setAttribute('stroke-linecap', 'round');
            hit.setAttribute('stroke-linejoin', 'round');
            hit.setAttribute('pointer-events', 'stroke');
            hit.setAttribute('class', 'ink-hit');
            var poly = document.createElementNS(SVG_NS, 'polyline');
            poly.setAttribute('points', points);
            poly.setAttribute('fill', 'none');
            poly.setAttribute('stroke', COLORS[a.color] || COLORS.black);
            poly.setAttribute('stroke-width', (a.strokeWidth || DEFAULT_STROKE_WIDTH) * unit);
            poly.setAttribute('stroke-linecap', 'round');
            poly.setAttribute('stroke-linejoin', 'round');
            stroke.appendChild(hit);
            stroke.appendChild(poly);
            return stroke;
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
            // Must match AnnotatedDocumentComposer.TEXT_BASELINE_RATIO. The mark's y is the TOP
            // of its box; a baseline a full em below it sits lower than where the composer
            // actually draws, so the preview would show the text below where the saved copy
            // puts it. On a clinical document what the provider positions has to be what is
            // filed and faxed.
            text.setAttribute('y', (a.y * h) + ((a.fontSize || 11) * unit * TEXT_BASELINE_RATIO));
            text.setAttribute('fill', COLORS[a.color] || COLORS.black);
            text.setAttribute('font-size', (a.fontSize || 11) * unit);
            text.setAttribute('font-family', 'CarlosAnnotation, sans-serif');
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
        if (state.saving) { return; }
        state.saved = false;
        a.id = ++state.seq;
        state.annotations.push(a);
        redrawPage(a.page);
        updateCounts();
    }

    function removeAnnotation(id) {
        if (state.saving) { return; }
        var a = findAnnotation(id);
        // Only a real removal is an unsaved change; an id that is already gone must not re-enable
        // Save, which would file a second copy of the marks just saved.
        if (!a) { return; }
        state.saved = false;
        var page = a.page;
        state.annotations.splice(state.annotations.indexOf(a), 1);
        redrawPage(page);
        updateCounts();
    }

    /** Records an in-place edit to an existing mark (a move or a text change). */
    function annotationChanged(a) {
        state.saved = false;
        redrawPage(a.page);
        updateCounts();
    }

    function findAnnotation(id) {
        return state.annotations.find(function (a) { return a.id === id; }) || null;
    }

    /**
     * Text, date and signature marks are objects placed at a point. They can be picked up and
     * moved from select and from the placing tools (text, date, signature), so a provider can
     * nudge a note while still adding others. The drawing tools (highlight, draw) never grab:
     * strokes are routinely drawn across existing marks (highlighting the line a note sits on,
     * circling a signature), so a press there always starts a new stroke. Ink and highlights
     * themselves move only with the select tool.
     */
    function isPlaced(a) {
        return a.type === 'text' || a.type === 'date' || a.type === 'signature';
    }

    function isEditableText(a) {
        return a.type === 'text' || a.type === 'date';
    }

    function canGrab(a) {
        if (state.saving) { return false; }
        if (state.tool === 'select') { return true; }
        return isPlaced(a) && (state.tool === 'text' || state.tool === 'date' || state.tool === 'signature');
    }

    /**
     * Rendered width of a note's text as a fraction of its page, or 0 when it cannot be measured.
     * The preview draws in the composer's font at its point size, so this tracks the width the
     * server measures; the 2% margin absorbs hinting differences between the two renderers.
     * Callers measure at the moment they need it: until the annotation font arrives the text is
     * laid out in a narrower fallback face, and a width cached from then lets a note overrun.
     */
    function noteWidth(a) {
        var svg = pagesEl.querySelector('.page[data-page="' + a.page + '"] svg');
        var text = svg ? svg.querySelector('text[data-id="' + a.id + '"]') : null;
        if (!text || !svg.clientWidth) { return 0; }
        try { return (advanceWidth(text) / svg.clientWidth) * 1.02; } catch (e) { return 0; }
    }

    /**
     * The width the text advances, spaces included. The composer checks the full string width
     * (PDFBox getStringWidth), so a trailing or repeated space counts; the painted glyph box
     * can leave it out. The stylesheet keeps note whitespace (white-space: pre) so the preview
     * lays out the same spaces the saved copy draws.
     */
    function advanceWidth(textEl) {
        var length = typeof textEl.getComputedTextLength === 'function' ? textEl.getComputedTextLength() : 0;
        return Math.max(length, textEl.getBBox().width);
    }

    /**
     * Sizes a note's box to its drawn text and pulls it back onto the page if the text would run
     * past the right edge. The composer refuses text that overruns the page, and the parser
     * requires x + w <= 1, so a note is kept inside both after it is placed or its text changes.
     * A note wider than the whole page cannot fit; it is left for the server's explicit error.
     */
    function fitNote(a) {
        var fit = fitNoteModel(a);
        if (fit) { redrawPage(a.page); }
        return Boolean(fit && fit.moved);
    }

    /**
     * The model half of fitNote: updates the note's box and reports what changed, without
     * redrawing. null when the note is unmeasurable or already fits.
     */
    function fitNoteModel(a) {
        var width = noteWidth(a);
        if (!width) { return null; }
        var x = Math.max(0, Math.min(a.x, 1 - width - EDGE_EPSILON));
        var w = Math.min(width, 1 - x - EDGE_EPSILON);
        if (x === a.x && w === a.w) { return null; }
        // The composer draws a note from its x alone (w only bounds the parser's check), so only
        // a changed x moves what a saved copy would show.
        var moved = x !== a.x;
        a.x = x;
        a.w = w;
        return { moved: moved };
    }

    /**
     * Fits every note to its drawn width; true when any note moved. Each page is redrawn once
     * after all of its notes are fitted rather than once per note: a note's width does not
     * depend on where the others sit, so nothing needs the intermediate redraws.
     */
    function fitAllNotes() {
        var moved = false;
        var pages = {};
        state.annotations.filter(isEditableText).forEach(function (a) {
            var fit = fitNoteModel(a);
            if (!fit) { return; }
            pages[a.page] = true;
            if (fit.moved) { moved = true; }
        });
        Object.keys(pages).forEach(function (page) { redrawPage(Number(page)); });
        return moved;
    }

    /**
     * Re-fits every note once the annotation font has loaded. A note placed or edited before
     * then was fitted to its width in the fallback face, and a wider real face can leave it
     * running past the page edge, which the composer refuses at save. A move in progress keeps
     * its own measurements, so the refit waits for it to end rather than shifting the mark under
     * the pointer. A save in flight waits too: its payload is already posted, and changing the
     * model under it would leave the page reporting as saved marks the copy does not have. The
     * deferred refit runs when the save ends, and a note it moves makes the page unsaved again.
     */
    function refitNotes() {
        if (state.moves > 0 || state.saving) {
            state.refitPending = true;
            return;
        }
        state.refitPending = false;
        if (fitAllNotes() && state.saved) {
            state.saved = false;
            updateCounts();
        }
    }

    /**
     * Called whenever a move ends, however it ends, to run a refit that waited for it. Moves are
     * counted rather than flagged: a mouse and a pen can drag on two pages at once, and the
     * first to finish must not release a refit under the other.
     */
    function moveEnded() {
        state.moves = Math.max(0, state.moves - 1);
        updateCounts();
        if (state.refitPending) { refitNotes(); }
    }

    /** The mark's extent in page fractions, used to keep a move on the page. */
    function markBounds(a) {
        if (a.type !== 'ink') { return { x: a.x, y: a.y, w: a.w, h: a.h }; }
        var xs = a.points.map(function (p) { return p[0]; });
        var ys = a.points.map(function (p) { return p[1]; });
        var x0 = Math.min.apply(null, xs);
        var y0 = Math.min.apply(null, ys);
        return { x: x0, y: y0, w: Math.max.apply(null, xs) - x0, h: Math.max.apply(null, ys) - y0 };
    }

    // The parser rejects x + w > 1. Stopping a hair inside the edge keeps a mark dragged hard
    // against it from failing the save on floating-point rounding.
    var EDGE_EPSILON = 1e-9;

    /**
     * Limits a move so the whole mark stays on its page; the server rejects anything past the
     * edge. A note is limited by its drawn text rather than its stored box: the box starts as a
     * fixed default, which would stop a short date stamp well short of the right margin and let a
     * long note run off the page.
     */
    function clampMove(a, dx, dy, b, drawnWidth) {
        var w = isEditableText(a) && drawnWidth > 0 ? drawnWidth : b.w;
        return {
            dx: Math.max(-b.x, Math.min(1 - b.x - w - EDGE_EPSILON, dx)),
            dy: Math.max(-b.y, Math.min(1 - b.y - b.h - EDGE_EPSILON, dy))
        };
    }

    /** Applies a finished move; returns false when nothing changed (so the caller redraws). */
    function moveAnnotation(a, dx, dy, drawnWidth) {
        if (state.saving || (!dx && !dy)) { return false; }
        if (a.type === 'ink') {
            a.points = a.points.map(function (p) {
                return [clamp(p[0] + dx), clamp(p[1] + dy)];
            });
        } else {
            // dx/dy are already limited by clampMove; this only guards against a negative zero.
            a.x = Math.max(0, a.x + dx);
            a.y = Math.max(0, a.y + dy);
            // The note was clamped by its drawn width, so that is the box it now fits.
            if (isEditableText(a) && drawnWidth > 0) { a.w = Math.min(drawnWidth, 1 - a.x - EDGE_EPSILON); }
        }
        annotationChanged(a);
        return true;
    }

    /**
     * Re-opens a text or date note for editing. Cancelling leaves it as it was; clearing it
     * removes the note, since the server refuses an empty text mark.
     */
    function editText(a) {
        if (state.saving) { return; }
        var value = window.prompt(t('promptEditText', 'Edit note (clear it to remove the note):'), a.text);
        if (value === null || value === a.text) { return; }
        if (!value.trim()) {
            removeAnnotation(a.id);
            return;
        }
        a.text = value;
        annotationChanged(a);
        fitNote(a);
    }

    /**
     * A press on a mark that was released without moving it. A note opens for editing (from
     * select, text or date); select deletes anything else. In a placing tool the click otherwise
     * does what it did before marks could be grabbed: it places a new mark at that point, so a
     * date can still be stamped inside a signature box.
     */
    function markClicked(a, page, nx, ny) {
        if (isEditableText(a) && (state.tool === 'select' || state.tool === 'text' || state.tool === 'date')) {
            editText(a);
        } else if (state.tool === 'select') {
            removeAnnotation(a.id);
        } else {
            placePoint(page, nx, ny);
        }
    }

    function updateCounts() {
        var count = state.annotations.length;
        document.getElementById('markCount').textContent = String(count);
        // Save is also held while a mark is being dragged: a save taken mid-drag would post the
        // mark where it was, and the drag could then not be applied under the in-flight save.
        var blocked = state.uncertain || state.saving || state.saved || state.moves > 0 || count === 0;
        document.getElementById('btnSave').disabled = blocked;
        document.getElementById('btnSaveFax').disabled = blocked;
    }

    /** Marks a save as in flight; the page attribute lets the stylesheet drop the move cursor. */
    function setSaving(saving) {
        state.saving = saving;
        pagesEl.toggleAttribute('data-saving', saving);
    }

    /* ---------- pointer interaction ---------- */

    function attachPointer(wrap) {
        var svg = wrap.querySelector('svg');
        var page = Number(wrap.dataset.page);
        var dragging = null;
        var moving = null;

        svg.addEventListener('pointerdown', function (event) {
            // Only the primary button (or a first touch/pen contact) acts on the page. A right- or
            // middle-click on a mark would otherwise run the click path on release, and in select
            // mode that silently deletes the mark the provider only meant to open a menu on.
            if (!event.isPrimary || event.button !== 0) { return; }
            // A new primary press means any earlier gesture is over, even if its pointerup never
            // arrived (a capture lost to a window switch); drop it rather than let it hijack this one.
            if (moving || dragging) {
                var staleMove = moving !== null;
                if (staleMove) { delete state.previews[moving.a.id]; }
                moving = null;
                dragging = null;
                redrawPage(page);
                if (staleMove) { moveEnded(); }
            }
            if (startMove(event)) { return; }
            if (state.saving || state.tool === 'select' || !wrap.querySelector('img').naturalWidth
                    || wrap.classList.contains('load-failed')) { return; }
            var rect = svg.getBoundingClientRect();
            var nx = (event.clientX - rect.left) / rect.width;
            var ny = (event.clientY - rect.top) / rect.height;

            if (state.tool === 'text' || state.tool === 'date' || state.tool === 'signature') {
                placePoint(page, nx, ny);
                return;
            }
            if (state.tool === 'highlight') { fetchWordBoxes(page); }
            dragging = { pointerId: event.pointerId, x0: nx, y0: ny, points: [[nx, ny]] };
            svg.setPointerCapture(event.pointerId);
        });

        // Each gesture belongs to the pointer that started it. A second finger on a touch screen
        // is not primary, so it never starts one, but its move and up events still reach this
        // overlay; without this check they would drag, then commit, the first finger's gesture.
        function owns(gesture, event) {
            return gesture !== null && gesture.pointerId === event.pointerId;
        }

        svg.addEventListener('pointermove', function (event) {
            if (moving) {
                if (owns(moving, event)) { continueMove(event); }
                return;
            }
            if (!owns(dragging, event)) { return; }
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
            if (moving) {
                if (owns(moving, event)) { finishMove(event); }
                return;
            }
            if (!owns(dragging, event)) { return; }
            var drag = dragging;
            dragging = null;
            if (svg.hasPointerCapture(event.pointerId)) { svg.releasePointerCapture(event.pointerId); }
            commitDrag(page, drag);
            redrawPage(page);
        });

        // A gesture the browser takes over (pointercancel) or whose capture is lost without a
        // pointerup ends with nothing committed: the moved mark snaps back, the half-drawn stroke
        // preview is dropped. After a normal pointerup both gestures are already cleared, so the
        // lostpointercapture that follows it is a no-op.
        function abandonGesture(event) {
            if (!owns(moving, event) && !owns(dragging, event)) { return; }
            var abandonedMove = moving !== null;
            if (abandonedMove) { delete state.previews[moving.a.id]; }
            moving = null;
            dragging = null;
            redrawPage(page);
            if (abandonedMove) { moveEnded(); }
        }
        svg.addEventListener('pointercancel', abandonGesture);
        svg.addEventListener('lostpointercapture', abandonGesture);

        // Moves are previewed with a transform on the mark's own element and written to the
        // model only on release, so a drag that is cancelled leaves the model untouched.
        function startMove(event) {
            var a = grabbableAt(event.clientX, event.clientY);
            if (!a) { return false; }
            event.preventDefault();
            var start = svg.getBoundingClientRect();
            // The grab point is kept in page fractions as well as screen pixels: a resize or a
            // scroll mid-drag moves the page under the pointer, and a pixel delta from the old
            // layout would then leave the mark drifting away from the pointer.
            moving = { pointerId: event.pointerId, a: a, x0: event.clientX, y0: event.clientY,
                fx0: start.width ? (event.clientX - start.left) / start.width : 0,
                fy0: start.height ? (event.clientY - start.top) / start.height : 0,
                dx: 0, dy: 0, moved: false, bounds: markBounds(a), drawnWidth: 0 };
            state.moves++;
            updateCounts();
            svg.setPointerCapture(event.pointerId);
            return true;
        }

        /**
         * The mark to pick up at a point: the topmost one this tool may grab, looking through
         * marks it may not (a highlight laid over a note must not hide the note from the text
         * tool). Visible marks win over the invisible halo that widens ink strokes, so a press on
         * a highlight or note next to a line takes that mark, not the line.
         */
        function grabbableAt(clientX, clientY) {
            var hits = document.elementsFromPoint ? document.elementsFromPoint(clientX, clientY) : [];
            var seen = {};
            var visible = [];
            var halos = [];
            for (var i = 0; i < hits.length; i++) {
                if (!svg.contains(hits[i]) || hits[i] === svg) { continue; }
                var owner = hits[i].closest('[data-id]');
                var a = owner ? findAnnotation(Number(owner.getAttribute('data-id'))) : null;
                if (!a || !canGrab(a) || seen[a.id]) { continue; }
                seen[a.id] = true;
                (hits[i].classList.contains('ink-hit') ? halos : visible).push(a);
            }
            return visible[0] || halos[0] || null;
        }

        function continueMove(event) {
            var rect = svg.getBoundingClientRect();
            if (!rect.width || !rect.height) { return; }
            var px = event.clientX - moving.x0;
            var py = event.clientY - moving.y0;
            // A few pixels of jitter on a click must not nudge the mark or swallow the click.
            if (!moving.moved && Math.abs(px) < MOVE_THRESHOLD_PX && Math.abs(py) < MOVE_THRESHOLD_PX) { return; }
            moving.moved = true;
            // Measured on every move, not once: the note may still be in the fallback face when
            // the drag starts, and the annotation font arriving mid-drag widens it.
            moving.drawnWidth = noteWidth(moving.a);
            var fx = (event.clientX - rect.left) / rect.width - moving.fx0;
            var fy = (event.clientY - rect.top) / rect.height - moving.fy0;
            var d = clampMove(moving.a, fx, fy, moving.bounds, moving.drawnWidth);
            moving.dx = d.dx;
            moving.dy = d.dy;
            // Kept in the shared state, not just on the elements, so a redraw mid-drag can put
            // it back; every element drawn for the mark (a note and its hit box) moves together.
            state.previews[moving.a.id] = { dx: d.dx, dy: d.dy };
            applyPreview(svg, moving.a.id, state.previews[moving.a.id], rect.width, rect.height);
        }

        function finishMove(event) {
            var done = moving;
            moving = null;
            delete state.previews[done.a.id];
            if (svg.hasPointerCapture(event.pointerId)) { svg.releasePointerCapture(event.pointerId); }
            if (done.moved) {
                // moveAnnotation redraws when it changes the model; otherwise clear the preview.
                if (!moveAnnotation(done.a, done.dx, done.dy, done.drawnWidth)) { redrawPage(page); }
            } else {
                var rect = svg.getBoundingClientRect();
                markClicked(done.a, page, clamp((event.clientX - rect.left) / rect.width),
                    clamp((event.clientY - rect.top) / rect.height));
            }
            moveEnded();
        }
    }

    /** Points-to-pixels scale for the page image this overlay sits on; 1 if it is not measurable. */
    function previewScale(svg) {
        var img = svg.parentNode ? svg.parentNode.querySelector('img') : null;
        return img ? pxPerPoint(img) : 1;
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
            // Same per-page scale redrawPage() uses. Hard-coding 2 here made the stroke visibly
            // change width the instant the pointer came up, because the committed mark is drawn
            // at strokeWidth * pxPerPoint while the preview was drawn at 2 device pixels.
            el.setAttribute('stroke-width', DEFAULT_STROKE_WIDTH * previewScale(svg));
        } else {
            el = document.createElementNS(SVG_NS, 'rect');
            el.setAttribute('x', Math.min(drag.x0, drag.x1 === undefined ? drag.x0 : drag.x1) * w);
            el.setAttribute('y', Math.min(drag.y0, drag.y1 === undefined ? drag.y0 : drag.y1) * h);
            el.setAttribute('width', Math.abs((drag.x1 === undefined ? drag.x0 : drag.x1) - drag.x0) * w);
            el.setAttribute('height', Math.abs((drag.y1 === undefined ? drag.y0 : drag.y1) - drag.y0) * h);
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
                strokeWidth: DEFAULT_STROKE_WIDTH, points: simplify(drag.points)
            });
            return;
        }
        var x = Math.min(drag.x0, drag.x1 === undefined ? drag.x0 : drag.x1);
        var y = Math.min(drag.y0, drag.y1 === undefined ? drag.y0 : drag.y1);
        var w = Math.abs((drag.x1 === undefined ? drag.x0 : drag.x1) - drag.x0);
        var h = Math.abs((drag.y1 === undefined ? drag.y0 : drag.y1) - drag.y0);
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

    /** Local calendar date. toISOString() is UTC, which is the previous or next day for a
     *  provider whose local date differs from it — a date stamp on a clinical document must
     *  read as the day they actually signed it. */
    function todayLocal() {
        var now = new Date();
        var month = String(now.getMonth() + 1).padStart(2, '0');
        var day = String(now.getDate()).padStart(2, '0');
        return now.getFullYear() + '-' + month + '-' + day;
    }

    function placePoint(page, nx, ny) {
        if (state.tool === 'signature') {
            addAnnotation({
                type: 'signature', page: page, color: 'black',
                // Clamped against the mark's OWN size, so it can be placed anywhere its box
                // still fits. Clamping against a larger span confined it to the left of the page.
                x: clamp(nx, SIGNATURE_W), y: clamp(ny, SIGNATURE_H),
                w: SIGNATURE_W, h: SIGNATURE_H
            });
            return;
        }
        var isDate = state.tool === 'date';
        var value = isDate ? todayLocal()
            : window.prompt(t('promptText', 'Note to add:'), '');
        if (!value) { return; }
        var note = {
            type: isDate ? 'date' : 'text', page: page, color: state.color,
            x: clamp(nx, TEXT_W), y: clamp(ny, TEXT_H), w: TEXT_W, h: TEXT_H,
            text: value, fontSize: 11
        };
        addAnnotation(note);
        // A note longer than the default box, placed near the right edge, would otherwise run
        // off the page and fail the save.
        fitNote(note);
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
            .then(function (r) {
                if (!r.ok) { throw new Error('Text layer unavailable'); }
                return r.json();
            })
            .then(function (data) {
                // An empty list is the normal answer for a page with no text layer, and is
                // cached as such so the page is not asked for again.
                if (!data || !Array.isArray(data.words)) { throw new Error('Invalid text layer'); }
                state.wordBoxes[page] = data.words;
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

    /**
     * Resolves once the CSRF token is actually in the hidden input.
     *
     * csrf-token.jspf fetches the token asynchronously on DOMContentLoaded. A provider who
     * marks up a page and hits Save before that settles would otherwise POST an empty token,
     * be rejected with an HTML error page, and see the save fail for no visible reason —
     * intermittently, which is the worst kind. Waiting on the bootstrap's own promise removes
     * the race; a rejected or absent promise still falls through to the read below, so the
     * failure surfaces as a normal save error rather than an unhandled rejection.
     */
    function csrfTokenReady() {
        if (csrfToken()) { return Promise.resolve(); }
        var ready = window.csrfTokenReady;
        if (!ready || typeof ready.then !== 'function') { return Promise.resolve(); }
        return ready.catch(function () { /* surfaces below as an empty token */ });
    }

    // How long a save waits for the annotation font before posting with the widths it has.
    var FONT_WAIT_MS = 3000;

    /**
     * Resolves once the annotation font has loaded (or failed, or FONT_WAIT_MS has passed). A
     * save waits on it and refits notes before taking its snapshot, so a note placed before the
     * font arrived is posted at its real width. Otherwise the refit would move the note after
     * the snapshot, leaving the page showing something other than the copy it reports as saved.
     */
    function annotationFontReady() {
        if (!state.fontReady) { return Promise.resolve(); }
        return Promise.race([state.fontReady, new Promise(function (resolve) {
            setTimeout(resolve, FONT_WAIT_MS);
        })]);
    }

    /** The model as the save endpoint takes it. */
    function savePayload() {
        return {
            sourceDigest: cfg.sourceDigest,
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
    }

    function save(thenFax) {
        if (state.uncertain || state.saving || state.saved || state.moves > 0 || !state.annotations.length) { return; }
        setSaving(true);
        setStatus(t('saving', 'Saving…'), 'busy');
        document.getElementById('btnSave').disabled = true;
        document.getElementById('btnSaveFax').disabled = true;

        annotationFontReady().then(function () {
            // This save's own snapshot is about to be taken, so it fits the notes directly;
            // refitNotes would defer to the save in progress.
            fitAllNotes();
            return csrfTokenReady();
        }).then(function () {
            return fetch(cfg.contextPath + '/documentManager/SaveAnnotatedDocument?docId='
                + encodeURIComponent(cfg.docId), {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                    'CSRF-TOKEN': csrfToken()
                },
                body: JSON.stringify(savePayload())
            });
        }).then(function (response) {
            return response.json().then(function (data) {
                return { ok: response.ok, data: data };
            });
        }).then(function (result) {
            setSaving(false);
            if (!result.ok || !result.data.success) {
                state.uncertain = result.data.retryable === false;
                setStatus(result.data && result.data.error
                    ? result.data.error
                    : t('saveFailed', 'The annotated document could not be saved.'), 'error');
                updateCounts();
                return;
            }
            state.saved = true;
            updateCounts();
            setStatus(t('saved', 'Saved as a new document.') + ' #' + result.data.documentNo, 'ok');
            if (thenFax) {
                // The page is leaving for the fax cover; a late refit must not raise the
                // unsaved-changes prompt on the way out.
                state.refitPending = false;
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
            setSaving(false);
            state.uncertain = true;
            setStatus('The save could not be confirmed. Check the patient’s documents before saving another copy.', 'error');
            updateCounts();
        }).then(function () {
            if (state.refitPending) { refitNotes(); }
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
        if (out.length <= 4000) { return out; }
        // Sample the complete stroke, including its final point; never silently chop its tail.
        return Array.from({ length: 4000 }, function (_, index) {
            return out[Math.round(index * (out.length - 1) / 3999)];
        });
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
        function resizeAllOverlays() {
            var wraps = pagesEl.querySelectorAll('.page');
            for (var n = 0; n < wraps.length; n++) { sizeOverlay(wraps[n]); }
        }
        window.addEventListener('resize', resizeAllOverlays);
        // Note hit boxes are measured from the rendered text, so re-measure once the annotation
        // font arrives; until then the text is laid out in a fallback face of different width.
        if (document.fonts && document.fonts.addEventListener) {
            document.fonts.addEventListener('loadingdone', function () {
                resizeAllOverlays();
                refitNotes();
            });
            // Fetch the annotation face now rather than when the first note is drawn, so notes
            // are measured (hit boxes, edge clamping) in the font the composer will use. Notes
            // placed before it arrives are refitted once it has.
            state.fontReady = document.fonts.load('11px CarlosAnnotation')
                .then(refitNotes, function () { /* fallback face */ });
        }
        window.addEventListener('beforeunload', function (event) {
            if (state.saving || (state.annotations.length && !state.saved)) {
                event.preventDefault();
                event.returnValue = '';
            }
        });
        selectTool('select');
    });
})();
