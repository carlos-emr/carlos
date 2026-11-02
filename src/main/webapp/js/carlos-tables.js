/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(() => {
    'use strict';
    function initialize(table) {
        if (table.dataset.tableReady) return;
        table.dataset.tableReady = 'true';
        const body = table.tBodies[0];
        const rows = Array.from(body.rows);
        const size = Number(table.dataset.pageSize) || rows.length || 1;
        let page = 0;
        let sortColumn = -1;
        let descending = false;
        const collator = new Intl.Collator(document.documentElement.lang || undefined, {sensitivity: 'base'});
        const headers = Array.from(table.tHead.rows[0].cells);
        const navigation = document.createElement('nav');
        navigation.className = 'pagelinks';
        navigation.setAttribute('aria-label', 'Table pages');
        const previous = document.createElement('button');
        previous.type = 'button';
        previous.textContent = 'Previous';
        const status = document.createElement('span');
        status.setAttribute('aria-live', 'polite');
        const next = document.createElement('button');
        next.type = 'button';
        next.textContent = 'Next';
        const first = document.createElement('button');
        first.type = 'button'; first.textContent = 'First';
        const last = document.createElement('button');
        last.type = 'button'; last.textContent = 'Last';
        const pages = document.createElement('span');
        navigation.append(first, previous, pages, next, last, status);
        if (rows.length > size) table.after(navigation);

        function render() {
            rows.forEach((row, index) => {
                row.hidden = index < page * size || index >= (page + 1) * size;
                row.classList.toggle('odd', index % 2 === 0);
                row.classList.toggle('even', index % 2 === 1);
                body.append(row);
            });
            previous.disabled = page === 0;
            first.disabled = page === 0;
            next.disabled = (page + 1) * size >= rows.length;
            last.disabled = next.disabled;
            status.textContent = ` ${rows.length ? page * size + 1 : 0}–${Math.min((page + 1) * size, rows.length)} of ${rows.length} `;
            pages.replaceChildren();
            const total = Math.ceil(rows.length / size);
            const start = Math.max(0, Math.min(page - 3, total - 7));
            for (let index = start; index < Math.min(total, start + 7); index++) {
                const button = document.createElement('button');
                button.type = 'button'; button.textContent = String(index + 1);
                button.setAttribute('aria-label', `Page ${index + 1}`);
                if (index === page) button.setAttribute('aria-current', 'page');
                button.addEventListener('click', () => { page = index; render(); });
                pages.append(button);
            }
        }
        function value(row, column) {
            const cell = row.cells[column];
            return {key: cell ? (cell.dataset.sortValue ?? cell.textContent).trim() : '',
                type: cell?.dataset.sortType ?? 'text'};
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
                return a < b ? -1 : a > b ? 1 : 0;
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
            const button = header.querySelector('.table-sort');
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
