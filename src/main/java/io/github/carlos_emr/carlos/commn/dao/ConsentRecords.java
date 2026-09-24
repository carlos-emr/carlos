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
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Chooses the one consent record that decides a patient's consent when several live records
 * exist for the same consent type.
 *
 * <p>The {@code Consent} table has no unique key on patient and consent type, so duplicates can
 * exist (#3845). Reading an arbitrary one could email a patient whose newer record opts out.
 * The rule is fail-safe: any live opt-out wins; otherwise the most recently edited record does.
 * Undated records count as the oldest, and equal dates fall back to the higher id.
 *
 * @since 2026-09-24
 */
public final class ConsentRecords {

    /** Most recently edited first; undated records last; then the higher id first. */
    public static final Comparator<Consent> MOST_RECENT_FIRST =
            Comparator.comparing(Consent::getEditDate, Comparator.nullsFirst(Comparator.<Date>naturalOrder()))
                    .thenComparing(Consent::getId, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()))
                    .reversed();

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
        return live.stream()
                .filter(Consent::isOptout)
                .min(MOST_RECENT_FIRST)
                .orElseGet(() -> live.stream().min(MOST_RECENT_FIRST).orElse(null));
    }
}
