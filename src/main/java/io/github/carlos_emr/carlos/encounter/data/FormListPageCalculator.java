/**
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
package io.github.carlos_emr.carlos.encounter.data;

/**
 * Works out which rows one page of the chart Forms list ({@code /encounter/ViewFormlist},
 * {@code encounter/formlist.jsp}) shows, and where its Previous and Next links lead.
 *
 * <p>The list holds every saved form of the patient, all form types together, and a page is a
 * window over that whole list: rows are counted across form types, not within each one
 * (issue #5684). The window comes from the page's legacy request parameters, {@code limit1}
 * (zero-based index of the page's first row) and {@code limit2} (rows per page). Both arrive
 * from the address bar and are checked separately, so a bad value never fails the page: a
 * missing, blank, nonnumeric or negative {@code limit1} starts at the first row, one past the
 * end of the list moves to the last page, and a missing, blank, nonnumeric or non-positive
 * {@code limit2} means {@value #DEFAULT_PAGE_SIZE} rows per page.</p>
 *
 * <p>Arithmetic is done in {@code long} so that a very large {@code limit1} or {@code limit2}
 * cannot overflow into a negative row index.</p>
 *
 * @since 2026-11-05
 */
public final class FormListPageCalculator {

    /** Rows per page when the request names no valid page size. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private FormListPageCalculator() {
    }

    /**
     * Returns the page of the Forms list a request asks for.
     *
     * <p>An offset at or past the end of a non-empty list moves to the start of the list's last
     * page, so a stale or edited address still shows rows and a working Previous link. An empty
     * list always yields offset {@code 0} with no Previous or Next link.</p>
     *
     * @param limit1 the request's {@code limit1}: index of the first row to show; {@code null},
     *               blank, nonnumeric and negative values mean {@code 0}
     * @param limit2 the request's {@code limit2}: rows per page; {@code null}, blank, nonnumeric,
     *               zero and negative values mean {@link #DEFAULT_PAGE_SIZE}
     * @param totalRows rows the list holds across every form type; a negative count is read as
     *                  {@code 0}
     * @return the page to render, never {@code null}
     */
    public static Page calculate(String limit1, String limit2, int totalRows) {
        int total = Math.max(0, totalRows);
        int size = parseAtLeast(limit2, 1, DEFAULT_PAGE_SIZE);
        int offset = parseAtLeast(limit1, 0, 0);
        if (offset >= total) {
            offset = total == 0 ? 0 : ((total - 1) / size) * size;
        }
        return new Page(offset, size, total);
    }

    private static int parseAtLeast(String value, int minimum, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= minimum ? parsed : fallback;
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    /**
     * One page of the Forms list.
     *
     * @param offset index, across all form types, of the first row this page shows
     * @param size rows per page, at least {@code 1}
     * @param totalRows rows the whole list holds
     */
    public record Page(int offset, int size, int totalRows) {

        /**
         * Validates the page's bounds.
         *
         * @throws IllegalArgumentException when {@code offset} or {@code totalRows} is negative
         *                                  or {@code size} is below {@code 1}
         */
        public Page {
            if (offset < 0 || size < 1 || totalRows < 0) {
                throw new IllegalArgumentException("Invalid Forms list page");
            }
        }

        /**
         * Tells whether the row at {@code rowIndex}, counted from {@code 0} across every form
         * type in display order, belongs on this page.
         *
         * @param rowIndex the row's position in the whole list
         * @return {@code true} when the row exists and is rendered on this page
         */
        public boolean contains(int rowIndex) {
            return rowIndex >= offset && rowIndex < end() && rowIndex < totalRows;
        }

        /**
         * Tells whether rows come before this page.
         *
         * @return {@code true} when a Previous link is shown
         */
        public boolean hasPrevious() {
            return offset > 0;
        }

        /**
         * Returns where the previous page starts.
         *
         * @return the {@code limit1} of the previous page; {@code 0} when it would start before
         *         the list
         */
        public int previousOffset() {
            return Math.max(0, offset - size);
        }

        /**
         * Tells whether rows follow this page.
         *
         * @return {@code true} when a Next link is shown
         */
        public boolean hasNext() {
            return end() < totalRows;
        }

        /**
         * Returns the {@code limit1} of the next page. Only meaningful when {@link #hasNext()} is
         * {@code true}; it is then a real row index and fits in an {@code int}.
         *
         * @return the index of the first row after this page
         */
        public int nextOffset() {
            return (int) Math.min(end(), Integer.MAX_VALUE);
        }

        private long end() {
            return (long) offset + size;
        }
    }
}
