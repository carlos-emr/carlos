/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;

import java.util.*;

public interface SystemPreferencesDao extends AbstractDao<SystemPreferences> {

    <T extends Enum<T>> SystemPreferences findPreferenceByName(Enum<T> name);

    <E extends Enum<E>> List<SystemPreferences> findPreferencesByNames(Class<E> clazz);

    <E extends Enum<E>> Map<String, Boolean> findByKeysAsMap(Class<E> clazz);

    Map<String, SystemPreferences> findByKeysAsPreferenceMap(List<String> keys);

    <T extends Enum<T>> boolean isReadBooleanPreference(Enum<T> name);

    <T extends Enum<T>> boolean isPreferenceValueEquals(Enum<T> preferenceName, String trueValueStr);

    /**
     * Sets a system-wide preference, creating its row only when no row with that name exists.
     *
     * <p>{@code SystemPreferences.name} has no unique key, so a find-then-insert can create a
     * duplicate when two first saves race, after which reads return an arbitrary row. This updates
     * every row with the name first (so pre-existing duplicates converge on the new value) and
     * inserts only when a count finds none. Must run inside a transaction.</p>
     *
     * <p><b>Concurrency guarantee, and its limits.</b> It depends on the caller's transaction
     * running at InnoDB REPEATABLE READ (or stricter). There, the UPDATE's locking scan of the
     * unindexed {@code name} column takes next-key locks on every row and gap it scans:</p>
     * <ul>
     *   <li>on a table with at least one row, a concurrent save blocks on those locks until this
     *       transaction commits, then its UPDATE sees the committed row and no second row is
     *       inserted;</li>
     *   <li>on an empty table both scans can hold the shared supremum gap lock, and the two
     *       inserts then deadlock: InnoDB rolls one back rather than letting both insert. The
     *       caller owns that recovery and must retry the whole transaction from outside it, as
     *       {@code LabDisplaySettings2Action} does with a bounded retry.</li>
     * </ul>
     * <p>At READ COMMITTED those gap locks are off and duplicates are possible; callers therefore
     * request REPEATABLE READ explicitly (see {@code LabPdfPreviewSettingsService#save}) instead
     * of relying on the server default. Even then duplicates already present are only repaired,
     * not prevented, and a unique key on {@code name} remains the only schema-level guarantee.</p>
     *
     * @param name the preference key
     * @param value the value to store
     */
    <T extends Enum<T>> void upsertPreference(Enum<T> name, String value);
}
