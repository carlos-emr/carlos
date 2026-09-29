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

import io.github.carlos_emr.carlos.commn.model.Consent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chooses the one consent record that decides a patient's consent when several live records
 * exist for the same consent type.
 *
 * <p>The {@code Consent} table has no unique key on patient and consent type, so duplicates can
 * exist (#3845). Reading an arbitrary one could email a patient whose newer record opts out.
 * The rule is fail-safe: any live opt-out wins. Otherwise a record the patient confirmed directly
 * (explicit) wins over an implied one, even a newer one: a later import or routine save must not
 * displace consent the patient gave in person. Among records equal on both, the most recently
 * edited wins; undated records count as the oldest, and equal dates fall back to the higher id.
 *
 * <p>In SQL the order is {@code optout DESC, explicit DESC, (edit_date IS NULL), edit_date DESC,
 * id DESC}. Anything that repairs stored duplicates, such as a migration or an import, must keep
 * the record this rule chooses.
 *
 * @since 2026-09-24
 */
public final class ConsentRecords {

    /** Most recently edited first; undated records last; then the higher id first. */
    public static final Comparator<Consent> MOST_RECENT_FIRST =
            Comparator.comparing(Consent::getEditDate, Comparator.nullsFirst(Comparator.<Date>naturalOrder()))
                    .thenComparing(Consent::getId, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()))
                    .reversed();

    /** The deciding record first: an opt-out, then an explicit record, then {@link #MOST_RECENT_FIRST}. */
    public static final Comparator<Consent> DECIDING_FIRST =
            Comparator.comparing(Consent::isOptout).reversed()
                    .thenComparing(Comparator.comparing(Consent::isExplicit).reversed())
                    .thenComparing(MOST_RECENT_FIRST);

    private ConsentRecords() {
    }

    /**
     * @param live the live (not deleted) records for one patient and consent type
     * @return the deciding record, or {@code null} when there is none
     */
    public static Consent effective(List<Consent> live) {
        if (live == null || live.isEmpty()) {
            return null;
        }
        List<Consent> decidingFirst = new ArrayList<>(live);
        decidingFirst.sort(DECIDING_FIRST);
        return decidingFirst.get(0);
    }

    /**
     * @param live the live (not deleted) records for one patient, of any consent types
     * @return the deciding record for each consent type, in the order the types first appear
     */
    public static List<Consent> effectivePerType(List<Consent> live) {
        if (live == null || live.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, List<Consent>> byType = new LinkedHashMap<>();
        for (Consent consent : live) {
            byType.computeIfAbsent(consent.getConsentTypeId(), type -> new ArrayList<>()).add(consent);
        }
        List<Consent> deciding = new ArrayList<>(byType.size());
        for (List<Consent> records : byType.values()) {
            deciding.add(effective(records));
        }
        return deciding;
    }
}
