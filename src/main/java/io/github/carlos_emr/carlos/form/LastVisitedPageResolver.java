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
package io.github.carlos_emr.carlos.form;

import java.util.Arrays;

/**
 * Picks the page a saved multi-page form reopens on from the {@code c_lastVisited} value stored in its row.
 *
 * <p>The reopen wrappers ({@code formmentalhealth.jsp}, {@code formrourke2006.jsp}) include
 * {@code /WEB-INF/jsp/form/<prefix><page>.jsp}, where {@code <page>} is the stored value. The pages
 * used to write that value as an unquoted attribute directly before {@code />}, so the browser posted
 * {@code referral/} or {@code p1/} and the row kept the slash (issue #4595). The include then named a
 * JSP that does not exist and the chart's saved-form entry answered HTTP 500. The pages now post a clean
 * value, but rows saved before the fix still carry the slash, and the value is read back from the
 * database into a dispatcher path, so it is matched against the form's own page names rather than
 * trusted.</p>
 *
 * @since 2026-10-22
 */
public final class LastVisitedPageResolver {

    private LastVisitedPageResolver() {
    }

    /**
     * Resolves a stored {@code c_lastVisited} value to one of the form's pages.
     *
     * @param stored the value read from the form's row; may be {@code null}
     * @param pages the form's page names; the first is the page a new or unreadable record opens on
     * @return the stored page with surrounding whitespace and trailing slashes removed when it names one of
     *         {@code pages}, otherwise the first page
     * @throws IllegalArgumentException when no page is given
     */
    public static String resolve(String stored, String... pages) {
        if (pages == null || pages.length == 0) {
            throw new IllegalArgumentException("a multi-page form has at least one page");
        }
        if (stored != null) {
            String page = stored.trim();
            // Rows saved before #4595 carry the slash the unquoted attribute picked up ("referral/", "p1/").
            while (page.endsWith("/")) {
                page = page.substring(0, page.length() - 1).trim();
            }
            if (Arrays.asList(pages).contains(page)) {
                return page;
            }
        }
        return pages[0];
    }
}
