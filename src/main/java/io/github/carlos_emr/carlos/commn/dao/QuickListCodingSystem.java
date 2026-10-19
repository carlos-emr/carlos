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
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Collections;
import java.util.List;

/**
 * The names a Dx quick-list row's coding system can be stored under.
 *
 * <p>The application's coding systems are named after their code tables ({@code icd9}, {@code ichppccode}). The
 * quick lists in the seed data (List1 on every new install, and "default" in the developer demo data) store their
 * ICHPPC codes under the older name {@code ichppc}, so a lookup by {@code ichppccode} never read them and the seeded
 * lists showed no codes (#4989). The quick-list lookups read both names; the stored rows are left as they are.</p>
 *
 * @since 2026-10-19
 */
public final class QuickListCodingSystem {

    /** The ICHPPC coding system, named after its code table. */
    static final String ICHPPC = "ichppccode";

    /** The older name the seeded quick-list rows store the ICHPPC coding system under. */
    static final String LEGACY_ICHPPC = "ichppc";

    private QuickListCodingSystem() {
    }

    /**
     * The names a quick-list row of the given coding system may be stored under, the given name first.
     *
     * @param codingSystem String a coding system name, as configured in {@code dxResearch_coding_sys}
     * @return List&lt;String&gt; {@code ichppccode} and {@code ichppc} for the ICHPPC coding system; otherwise just the
     *         given name
     */
    public static List<String> storedNames(String codingSystem) {
        if (ICHPPC.equals(codingSystem)) {
            return List.of(ICHPPC, LEGACY_ICHPPC);
        }
        return Collections.singletonList(codingSystem);
    }

    /**
     * The coding system a stored name stands for.
     *
     * @param storedName String a quick-list row's stored coding system
     * @return String {@code ichppccode} for the older {@code ichppc}; any other name unchanged
     */
    public static String canonical(String storedName) {
        return LEGACY_ICHPPC.equals(storedName) ? ICHPPC : storedName;
    }
}
