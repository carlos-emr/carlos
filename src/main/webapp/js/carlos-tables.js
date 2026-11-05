/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(() => {
    'use strict';
    function value(row, column) {
        const cell = row.cells[column];
        return {key: cell ? (cell.dataset.sortValue ?? cell.textContent).trim() : '',
            type: cell?.dataset.sortType ?? 'text'};
    }
    function initialize(table) {
        if (table.dataset.tableReady) return;
        const body = table.tBodies[0];
        const headerRow = table.tHead?.rows[0];
        if (!body || !headerRow) return;
        table.dataset.tableReady = 'true';
        const rows = Array.from(body.rows);
        const size = Number(table.dataset.pageSize) || rows.length || 1;
        let page = 0;
        let sortColumn = -1;
        let descending = false;
        const collator = new Intl.Collator(document.documentElement.lang || undefined, {sensitivity: 'base'});
        const labels = document.querySelector('[data-carlos-table-labels]')?.dataset || {
            first: 'First', previous: 'Previous', next: 'Next', last: 'Last',
            page: 'Page', pages: 'Table pages', of: 'of',
        };
        const headers = Array.from(headerRow.cells);
        const navigation = document.createElement('nav');
        navigation.className = 'pagelinks';
        navigation.setAttribute('aria-label', labels.pages);
        const previous = document.createElement('button');
        previous.type = 'button';
        previous.textContent = labels.previous;
        const status = document.createElement('span');
        status.setAttribute('aria-live', 'polite');
        const next = document.createElement('button');
        next.type = 'button';
        next.textContent = labels.next;
        const first = document.createElement('button');
        first.type = 'button'; first.textContent = labels.first;
        const last = document.createElement('button');
        last.type = 'button'; last.textContent = labels.last;
        const pages = document.createElement('span');
        const pageButtons = Array.from({length: Math.min(7, Math.ceil(rows.length / size))}, () => {
            const button = document.createElement('button');
            button.type = 'button';
            button.addEventListener('click', () => { page = Number(button.dataset.page); render(); });
            pages.append(button);
            return button;
        });
        navigation.append(first, previous, pages, next, last, status);
        if (rows.length > size) table.after(navigation);

        function render() {
            const focused = document.activeElement;
            const focusWasPage = pages.contains(focused);
            rows.forEach((row, index) => {
                row.hidden = index < page * size || index >= (page + 1) * size;
                row.querySelectorAll('input,select,textarea,button').forEach(control => {
                    if (row.hidden) {
                        if (control.type === 'checkbox' || control.type === 'radio') control.checked = false;
                        if (!control.disabled) {
                            control.dataset.tableDisabled = 'true';
                            control.disabled = true;
                        }
                    } else if (control.dataset.tableDisabled === 'true') {
                        if (control.type === 'checkbox' || control.type === 'radio') control.checked = false;
                        control.disabled = false;
                        delete control.dataset.tableDisabled;
                    }
                });
                row.classList.toggle('odd', index % 2 === 0);
                row.classList.toggle('even', index % 2 === 1);
                body.append(row);
            });
            previous.disabled = page === 0;
            first.disabled = page === 0;
            next.disabled = (page + 1) * size >= rows.length;
            last.disabled = next.disabled;
            status.textContent = ` ${rows.length ? page * size + 1 : 0}–${Math.min((page + 1) * size, rows.length)} ${labels.of} ${rows.length} `;
            const total = Math.ceil(rows.length / size);
            const start = Math.max(0, Math.min(page - 3, total - 7));
            pageButtons.forEach((button, offset) => {
                const index = start + offset;
                button.dataset.page = String(index);
                button.textContent = String(index + 1);
                button.setAttribute('aria-label', `${labels.page} ${index + 1}`);
                if (index === page) button.setAttribute('aria-current', 'page');
                else button.removeAttribute('aria-current');
            });
            if (focusWasPage || (navigation.contains(focused) && focused.disabled)) {
                pageButtons.find(button => Number(button.dataset.page) === page)?.focus();
            }
        }
        function compare(left, right) {
            const type = left.type === right.type ? left.type : 'text';
            left = left.key;
            right = right.key;
            if (left === right) return 0;
            if (!left) return -1;
            if (!right) return 1;
            if (type === 'number' && /^-?\d+$/.test(left) && /^-?\d+$/.test(right)) {
                const a = BigInt(left), b = BigInt(right);
                if (a === b) return 0;
                return a < b ? -1 : 1;
            }
            if (type === 'number' || type === 'date') {
                return Number(left) - Number(right);
            }
            return collator.compare(left, right);
        }
        function sort(column, reverse) {
            sortColumn = column;
            descending = reverse;
            rows.sort((a, b) => compare(value(a, column), value(b, column)) * (reverse ? -1 : 1));
            headers.forEach((header, index) => {
                if (index === column) header.setAttribute('aria-sort', reverse ? 'descending' : 'ascending');
                else header.removeAttribute('aria-sort');
            });
            page = 0;
            render();
        }
        headers.forEach((header, column) => {
            const label = header.querySelector('[data-table-sort]');
            if (!label) return;
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'table-sort';
            button.append(...label.childNodes);
            label.replaceWith(button);
            if (button) button.addEventListener('click', () => sort(column, sortColumn === column && !descending));
        });
        previous.addEventListener('click', () => { if (page > 0) { page--; render(); } });
        next.addEventListener('click', () => { if ((page + 1) * size < rows.length) { page++; render(); } });
        first.addEventListener('click', () => { page = 0; render(); });
        last.addEventListener('click', () => { page = Math.max(0, Math.ceil(rows.length / size) - 1); render(); });
        const initial = Number(table.dataset.defaultSort) - 1;
        if (initial >= 0 && initial < headers.length) sort(initial, table.dataset.defaultOrder === 'descending');
        else render();
    }
    function initializeAll() { document.querySelectorAll('table[data-carlos-table]').forEach(initialize); }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializeAll);
    else initializeAll();
})();
