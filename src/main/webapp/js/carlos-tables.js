/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Local sorting and paging for the JSTL tables that replaced displaytag (#5472).
 *
 * The pages must look and behave as they did under displaytag 3.8, so this reproduces its
 * rendered markup rather than inventing new controls:
 *   - a sortable header is <th class="sortable"><a href="#">Title</a></th>, and the sorted
 *     column also carries "sorted" plus "order1" (ascending) or "order2" (descending), so each
 *     page's own link and th styles apply exactly as before;
 *   - a paged table gets displaytag's default banner: <span class="pagebanner"> with the item
 *     summary and <span class="pagelinks"> with [First/Prev] 1, 2, 3 [Next/Last], above the
 *     table unless data-banner-placement="bottom" (paging.banner.placement); tables without
 *     data-page-size (displaytag pagesize="0") get no banner;
 *   - rows carry "odd"/"even" counted from the first row of each page;
 *   - like displaytag's default sort.amount=page, a sort orders the rows within each page;
 *     data-sort-amount="list" sorts the whole list and returns to the first page.
 * Banner text comes from carlosTableAssets.jspf (table.paging.* keys), whose English values are
 * displaytag's defaults. Rows outside the current page stay in the DOM but are hidden and their
 * controls disabled, so a form never submits a row the user cannot see.
 */
(() => {
    'use strict';
    const DEFAULT_LABELS = {
        itemName: 'item', itemsName: 'items',
        noItems: 'No {0} found.', oneItem: 'One {0} found.',
        allItems: '{0} {1} found, displaying all {2}.',
        someItems: '{0} {1} found, displaying {2} to {3}.',
        first: 'First', previous: 'Prev', next: 'Next', last: 'Last',
        goToPage: 'Go to page {0}', pages: 'Table pages',
    };
    // displaytag paging.banner.group_size: at most this many numbered page links.
    const GROUP_SIZE = 8;
    const NUMBER = /^[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?$/;
    const INTEGER = /^[-+]?\d+$/;

    function labels() {
        const template = document.querySelector('template[data-carlos-table-labels]');
        const configured = {};
        if (template) {
            for (const [name, text] of Object.entries(template.dataset)) {
                if (text) configured[name] = text;
            }
        }
        return Object.assign({}, DEFAULT_LABELS, configured);
    }

    /** MessageFormat-style {n} substitution; the patterns are plain text, never markup. */
    function format(pattern, ...values) {
        return pattern.replace(/\{(\d+)\}/g, (match, index) => (index < values.length ? String(values[index]) : match));
    }

    function numberFormat() {
        try {
            return new Intl.NumberFormat(document.documentElement.lang || undefined);
        } catch (error) {
            return {format: String};
        }
    }

    function cellKey(row, column) {
        const cell = row.cells[column];
        if (!cell) return {text: '', type: 'text'};
        return {text: (cell.dataset.sortValue ?? cell.textContent).trim(), type: cell.dataset.sortType || 'text'};
    }

    function compareNumbers(left, right) {
        if (INTEGER.test(left) && INTEGER.test(right)) {
            const a = BigInt(left);
            const b = BigInt(right);
            return a === b ? 0 : (a < b ? -1 : 1);
        }
        const a = Number(left);
        const b = Number(right);
        return a === b ? 0 : (a < b ? -1 : 1);
    }

    function setRowControls(row, hidden) {
        row.querySelectorAll('input,select,textarea,button').forEach(control => {
            if (hidden) {
                // A hidden page is not on screen, as it was not in the response under displaytag:
                // nothing on it may be submitted, and it comes back unchecked as a reload did.
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
    }

    function initialize(table) {
        if (table.dataset.tableReady) return;
        const body = table.tBodies[0];
        const headerRow = table.tHead?.rows[0];
        if (!body || !headerRow) return;
        table.dataset.tableReady = 'true';
        const text = labels();
        const numbers = numberFormat();
        const itemName = table.dataset.itemName || text.itemName;
        const itemsName = table.dataset.itemsName || text.itemsName;
        const original = Array.from(body.rows);
        const size = Math.max(0, Number.parseInt(table.dataset.pageSize, 10) || 0);
        const sortWholeList = table.dataset.sortAmount === 'list';
        const collator = new Intl.Collator(document.documentElement.lang || undefined, {sensitivity: 'base'});
        const headers = Array.from(headerRow.cells);
        let rows = original.slice();
        let page = 0;
        let sortColumn = -1;
        let ascending = true;

        let summary = null;
        let pageLinks = null;
        if (size > 0) {
            summary = document.createElement('span');
            summary.className = 'pagebanner';
            summary.setAttribute('aria-live', 'polite');
            pageLinks = document.createElement('span');
            pageLinks.className = 'pagelinks';
            pageLinks.setAttribute('role', 'navigation');
            pageLinks.setAttribute('aria-label', text.pages);
            if (table.dataset.bannerPlacement === 'bottom') table.after(summary, pageLinks);
            else table.before(summary, pageLinks);
            pageLinks.addEventListener('click', event => {
                const link = event.target.closest('a[data-table-page]');
                if (!link || !pageLinks.contains(link)) return;
                event.preventDefault();
                goTo(link.dataset.tablePage);
            });
        }

        function pageCount() {
            return size > 0 ? Math.ceil(rows.length / size) : 1;
        }

        function pageLink(label, target, title) {
            const link = document.createElement('a');
            link.href = '#';
            link.dataset.tablePage = target;
            link.textContent = label;
            if (title) link.title = title;
            return link;
        }

        function renderBanner(pages, start, end, refocus) {
            const total = rows.length;
            if (total === 0) summary.textContent = format(text.noItems, itemsName);
            else if (total === 1) summary.textContent = format(text.oneItem, itemName);
            else if (pages === 1) summary.textContent = format(text.allItems, numbers.format(total), itemsName, itemsName);
            else summary.textContent = format(text.someItems, numbers.format(total), itemsName,
                numbers.format(start + 1), numbers.format(end));

            // SmartListHelper.getPageNavigationBar: centre the current page in a group of eight.
            const current = page + 1;
            const numbered = pages === 0 ? [1] : [];
            const startPage = Math.max(Math.min(current - Math.floor(GROUP_SIZE / 2), pages - (GROUP_SIZE - 1)), 1);
            const endPage = Math.min(startPage + GROUP_SIZE - 1, pages);
            for (let number = startPage; number <= endPage; number++) numbered.push(number);
            const parts = [];
            numbered.forEach((number, index) => {
                if (index > 0) parts.push(', ');
                if (number === current) {
                    const selected = document.createElement('strong');
                    selected.textContent = numbers.format(number);
                    selected.dataset.tablePage = String(number);
                    selected.setAttribute('aria-current', 'page');
                    selected.tabIndex = -1;
                    parts.push(selected);
                } else {
                    parts.push(pageLink(numbers.format(number), String(number), format(text.goToPage, numbers.format(number))));
                }
            });
            const atFirst = current === 1;
            const atLast = current === pages;
            if (numbered.length > 1) {
                const head = atFirst ? [`[${text.first}/${text.previous}] `]
                    : ['[', pageLink(text.first, 'first'), '/', pageLink(text.previous, 'previous'), '] '];
                const tail = atLast ? [` [${text.next}/${text.last}]`]
                    : [' [', pageLink(text.next, 'next'), '/', pageLink(text.last, 'last'), ']'];
                parts.unshift(...head);
                parts.push(...tail);
            }
            pageLinks.replaceChildren(...parts);
            if (refocus) {
                const same = pageLinks.querySelector(`a[data-table-page="${refocus}"]`);
                (same || pageLinks.querySelector('strong[data-table-page]'))?.focus();
            }
        }

        function render(refocus) {
            const pages = pageCount();
            if (page > Math.max(0, pages - 1)) page = Math.max(0, pages - 1);
            const start = size > 0 ? page * size : 0;
            const end = size > 0 ? Math.min(start + size, rows.length) : rows.length;
            rows.forEach((row, index) => {
                const hidden = index < start || index >= end;
                row.hidden = hidden;
                setRowControls(row, hidden);
                // displaytag numbered the rows of each page from zero: the first is always "odd".
                const position = size > 0 ? index % size : index;
                row.classList.toggle('odd', position % 2 === 0);
                row.classList.toggle('even', position % 2 === 1);
            });
            if (summary) renderBanner(pages, start, end, refocus);
        }

        function goTo(target) {
            const pages = pageCount();
            const moves = {first: 0, previous: page - 1, next: page + 1, last: pages - 1};
            const next = target in moves ? moves[target] : Number.parseInt(target, 10) - 1;
            if (!Number.isInteger(next)) return;
            page = Math.min(Math.max(next, 0), Math.max(pages - 1, 0));
            render(target);
        }

        function comparator(column) {
            const keys = new Map(original.map(row => [row, cellKey(row, column)]));
            const filled = Array.from(keys.values()).filter(key => key.text);
            // A typed column sorts numerically only when every value is a number (dates are epoch
            // milliseconds); one stray value such as "$" or "N/A" sorts the column as text instead.
            const numeric = filled.length > 0
                && filled.every(key => (key.type === 'number' || key.type === 'date') && NUMBER.test(key.text));
            return (leftRow, rightRow) => {
                const left = keys.get(leftRow).text;
                const right = keys.get(rightRow).text;
                if (left === right) return 0;
                // displaytag's RowSorter puts empty values first, so last once the order is reversed.
                if (!left) return -1;
                if (!right) return 1;
                return numeric ? compareNumbers(left, right) : collator.compare(left, right);
            };
        }

        function sortBy(column, sortAscending) {
            sortColumn = column;
            ascending = sortAscending;
            const compare = comparator(column);
            const ordered = (a, b) => compare(a, b) * (sortAscending ? 1 : -1);
            if (size > 0 && !sortWholeList) {
                // sort.amount=page: each page keeps its own rows and only their order changes.
                rows = [];
                for (let start = 0; start < original.length; start += size) {
                    rows.push(...original.slice(start, start + size).sort(ordered));
                }
            } else {
                rows = original.slice().sort(ordered);
                page = 0;
            }
            rows.forEach(row => body.append(row));
            headers.forEach((header, index) => {
                const sorted = index === column;
                header.classList.toggle('sorted', sorted);
                header.classList.toggle('order1', sorted && sortAscending);
                header.classList.toggle('order2', sorted && !sortAscending);
                if (sorted) header.setAttribute('aria-sort', sortAscending ? 'ascending' : 'descending');
                else header.removeAttribute('aria-sort');
            });
            render();
        }

        headers.forEach((header, column) => {
            const label = header.querySelector('[data-table-sort]');
            if (!label) return;
            header.classList.add('sortable');
            // A real link, as displaytag rendered it, so the page's own a / th a styles apply.
            const link = document.createElement('a');
            link.href = '#';
            link.setAttribute('role', 'button');
            link.className = 'table-sort';
            link.append(...label.childNodes);
            label.replaceWith(link);
            link.addEventListener('click', event => {
                event.preventDefault();
                sortBy(column, !(sortColumn === column && ascending));
            });
            link.addEventListener('keydown', event => {
                // Enter already follows the link; Space activates it as a button instead of scrolling.
                if (event.key === ' ' || event.key === 'Spacebar') {
                    event.preventDefault();
                    link.click();
                }
            });
        });

        const initial = Number.parseInt(table.dataset.defaultSort, 10) - 1;
        if (initial >= 0 && initial < headers.length) sortBy(initial, table.dataset.defaultOrder !== 'descending');
        else render();
    }

    function initializeAll() { document.querySelectorAll('table[data-carlos-table]').forEach(initialize); }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializeAll);
    else initializeAll();
})();
